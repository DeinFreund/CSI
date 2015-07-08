package zkcbai.unitHandlers;

import com.springrts.ai.oo.AIFloat3;
import com.springrts.ai.oo.clb.OOAICallback;
import com.springrts.ai.oo.clb.Unit;
import com.springrts.ai.oo.clb.UnitDef;
import java.awt.Color;
import java.awt.Graphics2D;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import zkcbai.Command;
import zkcbai.UnitFinishedListener;
import zkcbai.UpdateListener;
import zkcbai.helpers.AreaChecker;
import zkcbai.helpers.AreaZoneChangeListener;
import zkcbai.helpers.ZoneManager;
import zkcbai.helpers.ZoneManager.Area;
import zkcbai.helpers.ZoneManager.Mex;
import zkcbai.helpers.ZoneManager.Zone;
import zkcbai.unitHandlers.units.AISquad;
import zkcbai.unitHandlers.units.AIUnit;
import zkcbai.unitHandlers.units.Enemy;
import zkcbai.unitHandlers.units.tasks.BuildTask;
import zkcbai.unitHandlers.units.tasks.Task;

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
/**
 *
 * @author User
 */
public class BuilderHandler extends UnitHandler implements UpdateListener, UnitFinishedListener, AreaZoneChangeListener {

    protected float avgMetalIncome = 0;
    protected float energyIncome = 0;

    protected final int MAX_PLANNING = 13;
    protected final int MIN_PLANNING = 1; 
    protected final int MIN_PYLON_ENERGY_INCOME = 200;
    protected int PLANNING = MAX_PLANNING; //How many steps the grid should plan ahead
    protected float HEURISTIC = 0;
    protected float MAX_HEURISTIC = 0.7f;
    
    
    protected Set<BuildTask> energyConstructions = new HashSet<>();
    
    protected Map<GridNode, Map<Float, GridNode> > connections = new HashMap();

    protected Set<GridNode> gridNodes = new HashSet<GridNode>(){
        @Override
        public boolean add(GridNode gn){
            Map<Float, GridNode> newconns = new TreeMap();
            for (GridNode n : gridNodes){
                connections.get(n).put(n.distanceTo(gn), gn);
                newconns.put(n.distanceTo(gn), n);
            }
            
            connections.put(gn, newconns);
            return super.add(gn);
        }
        
        @Override
        public boolean remove(Object gn2){
            if (gn2 == null) return false;
            if (!(gn2 instanceof GridNode)) throw new RuntimeException("you're doing it wrong!");
            GridNode gn = (GridNode) gn2;
            boolean retval = super.remove(gn);
            for (GridNode n : gridNodes){
                connections.get(n).remove(n.distanceTo(gn));
            }
            connections.remove(gn);
            return retval;
        }
    };

    protected Map<Integer, GridNode> unitGridNodeFinder = new TreeMap<>();
    protected Map<Integer, GridNode> taskGridNodeFinder = new TreeMap<>();
    

    public BuilderHandler(Command cmd, OOAICallback clbk) {
        super(cmd, clbk);
        cmd.addUpdateListener(this);
        cmd.addUnitFinishedListener(this);
        cmd.areaManager.addAreaZoneChangeListener(this);
    }

    @Override
    public AIUnit addUnit(Unit u) {
        AIUnit au = new AIUnit(u, this);
        aiunits.put(u.getUnitId(), au);
        return au;
    }

    protected float getTargetEnergyIncome() {
        return (command.getCurrentFrame() / 2000f * 0.1f - 0.1f) * avgMetalIncome;
    }

    @Override
    public void troopIdle(AIUnit u) {
        BuildTask best = null;
        float bestscore = -1e9f;
        for (BuildTask bt : energyConstructions){
            float score = -u.distanceTo(bt.getPos()) - bt.getWorkers().size() * 500;
            if (best == null || score > bestscore){
                bestscore = score;
                best = bt;
            }
        }
        if (best != null){
            if (best.isDone() || best.isAborted()) throw new RuntimeException("Didn't clean task from energyConstructions");
            u.queueTask(best);
        }
    }

    protected void endedTask(final Task t) {

        if (t instanceof BuildTask) {
            
            //command.mark(((BuildTask)t).getPos(), "removed grid");
            energyConstructions.remove((BuildTask) t);
            command.addSingleUpdateListener(new UpdateListener() {

                @Override
                public void update(int frame) {

                    gridNodes.remove(taskGridNodeFinder.get(t.getTaskId()));
                    taskGridNodeFinder.remove(t.getTaskId());
                }
            }, command.getCurrentFrame() + 10);
        }
    }

    @Override
    public void abortedTask(Task t) {
        endedTask(t);

    }

    @Override
    public void finishedTask(Task t) {
        endedTask(t);
    }

    @Override
    public void removeUnit(AIUnit u) {
        aiunits.remove(u.getUnit().getUnitId());
    }

    @Override
    public void unitDestroyed(AIUnit u, Enemy e) {
        removeUnit(u);
        if (u.getMakesEnergy() > 0) {
            energyIncome -= u.getMakesEnergy();
            //command.debug("Energy income is now: " + energyIncome);
        }
        if (unitGridNodeFinder.containsKey(u.getUnit().getUnitId())) {
            gridNodes.remove(unitGridNodeFinder.get(u.getUnit().getUnitId()));
            unitGridNodeFinder.remove(u.getUnit().getUnitId());
        }
    }

    @Override
    public void unitDestroyed(Enemy e, AIUnit killer) {
    }

    @Override
    public void reportSpam() {
        throw new RuntimeException("I spammed MoveTasks!");
    }

    @Override
    public void troopIdle(AISquad s) {
    }

    private Random rnd = new Random();
    
    protected BuildTask queueEnergyBuilding() {
        //command.debug("calculating MST for " + gridNodes.size() + " nodes..");

        class Edge {

            GridNode start, end;

            public Edge(GridNode start, GridNode end) {
                this.start = start;
                this.end = end;
            }
        }

        List<Edge> unbuiltEdges = new ArrayList();

        long time = System.currentTimeMillis();
        // calc MST
        if (!gridNodes.isEmpty()) {
            TreeMap<Float, GridNode> dists = new TreeMap();
            TreeMap<GridNode, Float> best = new TreeMap();
            TreeMap<GridNode, GridNode> prev = new TreeMap(); // for debug
            Set<GridNode> unconnected = new HashSet(gridNodes);

            dists.put(0f, gridNodes.iterator().next());
            unconnected.remove(dists.firstEntry().getValue());
            best.put(dists.firstEntry().getValue(), 0f);
            prev.put(dists.firstEntry().getValue(), dists.firstEntry().getValue());
            while (unconnected.size() > 0 && !dists.isEmpty()) {
                GridNode node = dists.firstEntry().getValue();
                float dist = dists.firstKey();
                dists.remove(dists.firstKey());
                
                if (dist > best.get(node)) {
                    //if (dists.isEmpty()) command.debug("ERROR: node was never added to dists");
                    continue;
                }
                
                //unconnected.remove(node);
                if (prev.get(node).distanceTo(node)> 0) {
                    unbuiltEdges.add(new Edge(prev.get(node), node));
                    unbuiltEdges.add(new Edge(node, prev.get(node)));
                    //command.getCallback().getMap().getDrawer().addLine(prev.get(node).pos, node.pos);
                }

                int max = (int)Math.round((1-HEURISTIC) * connections.get(node).size());
                int i = 0;
                for (GridNode to : connections.get(node).values()) {
                    if (++i >= max) break;
                    //command.debug( !best.containsKey(to) +"||"+best.get(to) + ">"+ node.distanceTo(to));
                    if (!best.containsKey(to) || best.get(to) > node.distanceTo(to)) {
                        best.put(to, node.distanceTo(to));
                        dists.put(node.distanceTo(to) - rnd.nextFloat() / 100f, to);
                        prev.put(to, node);
                    }
                }
            }
        }
        
        long time2 = System.currentTimeMillis();

        class BuildPossibility {

            UnitDef building;
            AIFloat3 pos;
            float cost; // cost, try to minimize

            public BuildPossibility(UnitDef building, AIFloat3 pos, float score) {
                this.building = building;
                this.cost = score;
                this.pos = new AIFloat3(pos);
            }
        }

        List<BuildPossibility> possibilities = new ArrayList();

        /*
        for (GridNode node : gridNodes) {
            if (node.building.getName().equalsIgnoreCase("cormex")) {
                AIFloat3 pos = command.getCallback().getMap().findClosestBuildSite(command.getCallback().getUnitDefByName("armsolar"), node.pos, 500, 3, 0);
            //    possibilities.add(new BuildPossibility(command.getCallback().getUnitDefByName("armsolar"), pos, Float.MAX_VALUE));
            }
        }*/

        final UnitDef[] eBuildings = new UnitDef[]{command.getCallback().getUnitDefByName("armsolar"), 
            command.getCallback().getUnitDefByName("armestor"), command.getCallback().getUnitDefByName("armwin")};

                
        final float searchRadiusMult = 0.4f;
        for (final Edge edge : unbuiltEdges) { //each edge is contained twice, so only attaching to start has to be evalued
            for (final UnitDef building : eBuildings) {
                AIFloat3 dir = new AIFloat3(edge.end.pos);
                dir.sub(edge.start.pos);
                dir.normalize();
                
                float cost = 0;
                float mindist = 5000;
                for (AIUnit au : aiunits.values()) {
                    mindist = Math.min(mindist, au.distanceTo(edge.start.pos));
                }
                cost += (mindist + 1000) / 1000f;
                cost += command.areaManager.getArea(edge.start.pos).getDanger() * 1000;
                cost -= 0.25 * (building.getCustomParams().containsKey("income_energy") ? Float.valueOf(building.getCustomParams().get("income_energy")) : 0f);
                cost += 0.25f * building.getCost(command.metal) / 35f;
                if (building.getName().equals("armestor")) {
                    if (command.getCallback().getEconomy().getIncome(command.energy) > MIN_PYLON_ENERGY_INCOME) {
                        cost *= 0.5;
                    } else {
                        cost *= 2;
                    }
                }
                final AIFloat3 pos = new AIFloat3(edge.start.pos);
                if (edge.end.distanceTo(edge.start) < 0.92 * Float.valueOf(building.getCustomParams().get("pylonrange"))){
                    //Try to build in mid
                    pos.add(edge.end.pos);
                    pos.scale(0.5f);
                    if (!command.isPossibleToBuildAt(building, pos, 0)){
                        pos.set(command.getCallback().getMap().findClosestBuildSite(building, pos, searchRadiusMult * command.areaManager.getAreas().get(0).getEnclosingRadius(), 3, 0));
                    }
                    
                    if (command.isPossibleToBuildAt(building, pos, 0) &&
                            Command.distance2D(pos, edge.start.pos) < 0.95*(edge.start.range + Float.valueOf(building.getCustomParams().get("pylonrange"))) &&
                            Command.distance2D(pos, edge.end.pos) < 0.95*(edge.end.range + Float.valueOf(building.getCustomParams().get("pylonrange"))) && 
                            command.areaManager.getArea(pos).isReachable()) {
                        //command.mark(pos, "midposs");
                        possibilities.add(new BuildPossibility(building, pos, cost * 0.65f));
                    }
                }
                //try to build on line
                pos.set(dir);
                pos.scale(0.92f * (edge.start.range + Float.valueOf(building.getCustomParams().get("pylonrange"))));
                pos.add(edge.start.pos);
                if (command.isPossibleToBuildAt(building, pos, 0) && command.areaManager.getArea(pos).isReachable()) {
                    possibilities.add(new BuildPossibility(building, pos, cost));
                } else {
                    //try to build near start
                    pos.set(dir);
                    pos.scale(0.92f * (edge.start.range + Float.valueOf(building.getCustomParams().get("pylonrange"))));
                    pos.add(edge.start.pos);
                    Area buildArea =(command.areaManager.getArea(pos).getNearestArea(new AreaChecker() {

                        @Override
                        public boolean checkArea(ZoneManager.Area a) {
                            AIFloat3 bpos = command.getCallback().getMap().findClosestBuildSite(building, a.getPos(), searchRadiusMult * a.getEnclosingRadius(), 3, 0);
                            return a.isReachable() && command.isPossibleToBuildAt(building, bpos, 0) 
                                    && Command.distance2D(bpos, edge.start.pos) < 0.92*(edge.start.range + Float.valueOf(building.getCustomParams().get("pylonrange")));
                        }
                    }));
                    if (buildArea == null) continue;
                    AIFloat3 bpos = command.getCallback().getMap().findClosestBuildSite(building, buildArea.getPos(), searchRadiusMult * buildArea.getEnclosingRadius(), 3, 0);
                    float efficiency = distance(bpos, edge.start.pos) / (edge.start.range + Float.valueOf(building.getCustomParams().get("pylonrange")));
                    if (pos.x > 0 && distance(bpos, edge.start.pos) < edge.start.range + Float.valueOf(building.getCustomParams().get("pylonrange")) && 
                            //has to reduce distance between circles(greedy):
                            distance(edge.start.pos,edge.end.pos) - edge.start.range - edge.end.range > distance(bpos, edge.end.pos) - edge.end.range
                            - Float.valueOf(building.getCustomParams().get("pylonrange")) && command.isPossibleToBuildAt(building, bpos, 0)) {
                        //command.mark(bpos, "possible");
                        possibilities.add(new BuildPossibility(building, bpos, cost / efficiency));
                    }
                }
            }
        }

        BuildPossibility best = null;
        for (BuildPossibility bp : possibilities) {
            //if (bp.building.getName().equals("armestor")){
                //command.mark(bp.pos, "pylon for " + bp.cost);
            //}
            if (best == null || best.cost > bp.cost) {
                best = bp;
            }
        }
        BuildTask buildTask = null;
        if (best != null) {
            //command.mark(best.pos, "building "+ best.building.getHumanName() + " for " + best.cost);
            //command.debug("building " + best.building.getHumanName() + " @ " + best.pos.toString() + " for " + best.cost);
            if (!command.isPossibleToBuildAt(best.building, best.pos, 0))command.debug("HONK!");
            buildTask = new BuildTask(best.building, best.pos, 0, this, clbk, command);
        } else {
            //command.debug("No build possibility found for energy!");
        }
        long time3 = System.currentTimeMillis();
        if (time3 - time > 100) {
            command.debug("QueueBuildTask took " + (time3 - time2) + "ms selecting, " + (time2 - time) + "ms calculating MST.");
        }

        return buildTask;
    }

    public Collection<GridNode> getGridNodes() {
        return gridNodes;
    }

    public float getEnergyUnderConstruction() {
        float res = 0;
        for (BuildTask bt : energyConstructions) {
            res += (bt.getBuilding().getCustomParams().containsKey("income_energy") ? Float.valueOf(bt.getBuilding().getCustomParams().get("income_energy")) : 0f);
        }
        return res;
    }
    
    /**
     * 
     * @return <b>approximate</b> amount of metal under construction
     */
    public float getMetalUnderConstruction() {
        float res = 0;
        for (BuildTask bt : energyConstructions) {
            res += (bt.getBuilding().getName().equalsIgnoreCase("cormex") ? 1.5 : 0f);
        }
        return res;
    }


    protected int lastEnergyCheck = 0;


    private Color generateRandomColor() {
        int red = rnd.nextInt(3) * 255/2;
        int green = rnd.nextInt(3) * 255 /2;
        int blue = rnd.nextInt(3) * 255 /2;


        Color color = new Color(red, green, blue);
        return color;
    }
    
    protected boolean isConnected(GridNode start, GridNode end) {
        return isConnected(start, new HashSet<>(Arrays.asList(end)), new HashSet<GridNode>());
    }

    protected boolean isConnected(GridNode start, Set<GridNode> end, Set<GridNode> avoid) {
        Set<GridNode> unvisited = new HashSet(gridNodes);
        if (avoid.contains(start)) {
            throw new RuntimeException("Avoid contains start!");
        }
        unvisited.removeAll(avoid);
        Queue<GridNode> pq = new ArrayDeque();
        pq.add(start);
        unvisited.remove(start);
        while (!pq.isEmpty()){
            GridNode pos = pq.poll();
            if (end.contains(pos)) end.remove(pos);
            
            Collection<GridNode> toRemove = new ArrayList();
            for (GridNode gn : unvisited){
                if (gn.distanceTo(pos) < 0){
                    pq.add(gn);
                    toRemove.add(gn);
                    if (gbefore != null) {
                        gbefore.setColor(generateRandomColor());
                        gbefore.drawLine(Math.round(gn.pos.x) / imgscale, Math.round(gn.pos.z) / imgscale,
                                Math.round(pos.pos.x) / imgscale, Math.round(pos.pos.z) / imgscale);
                        command.getCallback().getMap().getDrawer().addLine(gn.pos, pos.pos);
                    }
                }
            }
            unvisited.removeAll(toRemove);
        }
        return end.isEmpty();
    }
    
    public BuildTask requestCaretaker(AIFloat3 pos){
        BuildTask bt = new BuildTask(command.getCallback().getUnitDefByName("armnanotc"), pos, this, clbk, command, 4);
        energyConstructions.add(bt);
        return bt;
    }
    
    protected boolean isArticulationPoint(GridNode gridNode) {
        List<GridNode> neighbours = new ArrayList();
        for (GridNode gn : gridNodes) {
            if (gn.distanceTo(gridNode) < 0 && !gn.equals(gridNode)) {
                neighbours.add(gn);
            }
        }
        if (neighbours.isEmpty()) return true;
        GridNode last = neighbours.get(neighbours.size() - 1);
        neighbours.remove(neighbours.size() - 1);
        return neighbours.isEmpty() || !isConnected(last, new HashSet(neighbours), new HashSet<>(Arrays.asList(gridNode)));
    }
    int imgscale = 10;
    Graphics2D gbefore;
    
    int lastConCheck = 0;
    int lastQueueSize = 0;
    
    int lastMexCheck = -600;
    
    @Override
    public void update(int frame) {
        float adaption = 0.05f;
        avgMetalIncome = (1 - adaption) * avgMetalIncome + command.getCallback().getEconomy().getIncome(command.metal) * adaption;
        
        if (frame - lastConCheck > 600){
            lastConCheck = frame;
            if (energyConstructions.size() - lastQueueSize > 0 && energyConstructions.size() > aiunits.size() || aiunits.isEmpty()){
                command.getFactoryHandler().requestConstructor();
                lastQueueSize = energyConstructions.size();
            }
                
        }
        
        if (avgMetalIncome + getMetalUnderConstruction() < energyIncome + getEnergyUnderConstruction() && frame - lastMexCheck > 100 && !aiunits.isEmpty()){
            float toBuild = energyIncome + getEnergyUnderConstruction() - avgMetalIncome - getMetalUnderConstruction();
            TreeMap<Float, Mex> dists = new TreeMap<>();
            for (Mex m : command.areaManager.getMexes()){
                    dists.put(aiunits.values().iterator().next().distanceTo(m.pos), m);
            }
            for (Mex m : dists.values()){
                if (m.getBuildTask() != null || command.areaManager.getArea(m.pos).getZone() != Zone.own) continue;
                toBuild -= m.getIncome();
                BuildTask bt = m.createBuildTask(this);
                GridNode gn = new GridNode(bt);
                energyConstructions.add(bt);
                gridNodes.add(gn);
                taskGridNodeFinder.put(bt.getTaskId(), gn);
                if (toBuild < 0) break;
            }
        }

        if ((1.1 * command.getCallback().getEconomy().getUsage(command.energy) > energyIncome + getEnergyUnderConstruction())
                && frame - lastEnergyCheck > 60 && command.economyManager.getRemainingEnergyBudget() > 0) {
            long time = System.currentTimeMillis();
            lastEnergyCheck = frame;    
            
            int mwidth = command.getCallback().getMap().getWidth() * 8;
            int mheight = command.getCallback().getMap().getHeight() * 8;
            //BufferedImage before = new BufferedImage(mwidth/ imgscale, mheight/imgscale, BufferedImage.TYPE_INT_RGB);
            //gbefore =  before.createGraphics();
            
            List<BuildTask> buildTasks = new ArrayList();
            List<GridNode> gridNodes = new ArrayList();
            int planning = PLANNING;
            boolean[] useless = new boolean[planning];
            for (int i =0; i < planning; i++){
                buildTasks.add(queueEnergyBuilding());
                if (buildTasks.get(i) == null) {
                    planning = i;
                    command.debug("Only planning grid " + i + " steps ahead.");
                } else {
                    GridNode gridNode = new GridNode(buildTasks.get(i));
                    /*for (GridNode gn : this.gridNodes){
                        if (Command.distance2D(gn.pos, gridNode.pos) < 100){
                            useless[i] = true;
                        }
                    }*/
                    gridNodes.add(gridNode);
                    this.gridNodes.add(gridNode);
                }
            }

            
            //check for completely superfluous buildings
            /*
            for (int i = 0; i < planning; i++){
                for (int i2 = i+1; i2 < planning; i2++){
                    float dist = gridNodes.get(i).distanceTo(gridNodes.get(i2)) + gridNodes.get(i).range + gridNodes.get(i2).range;
                    if (gridNodes.get(i).range - dist > gridNodes.get(i2).range){
                        useless[i2] = true;
                        command.debug("scrapped a building");
                    }
                    if (gridNodes.get(i2).range - dist > gridNodes.get(i).range){
                        useless[i] = true;
                        command.debug("scrapped a building");
                    }
                }
            }*/
            for (int i = 0; i < planning; i++) {
                if (useless[i] || !isArticulationPoint(gridNodes.get(i))) {
                    useless[i] = true;
                    this.gridNodes.remove(gridNodes.get(i));
                    //gbefore.setColor(Color.red);
                } else {
                    //gbefore.setColor(Color.green);
                }
                //AIFloat3 pos = gridNodes.get(i).pos;
//                float radius = gridNodes.get(i).range;
                //gbefore.drawOval(Math.round(pos.x - radius)/imgscale, Math.round(pos.z - radius)/imgscale,
                //        Math.round(radius*2)/imgscale, Math.round(radius*2)/imgscale);
            }
            this.gridNodes.removeAll(gridNodes);
            
//            for (GridNode gn : this.gridNodes) {
//
//                gbefore.setColor(new Color(120, 120, 120));
//                AIFloat3 pos = gn.pos;
//                float radius = gn.range;
//                gbefore.drawOval(Math.round(pos.x - radius)/imgscale, Math.round(pos.z - radius)/imgscale,
//                        Math.round(radius*2)/imgscale, Math.round(radius*2)/imgscale);
//            }
//            try {
//                if (planning > 2)
//                ImageIO.write(before, "png", new File("C:/Temp/aidebug/img" + frame + ".png"));
//            } catch (IOException ex) {
//                command.debug("exception writing image", ex);
//            }
            
            for (int i = 0; i < planning; i++){
                if (useless[i]) continue;
                energyConstructions.add(buildTasks.get(i));
                this.gridNodes.add(gridNodes.get(i));
                taskGridNodeFinder.put(buildTasks.get(i).getTaskId(), gridNodes.get(i));
                command.economyManager.useEnergyBudget(buildTasks.get(i).getBuilding().getCost(command.metal));
//                if (buildTasks.get(i).getBuilding().getName().equals("armestor")){
//                    command.mark(buildTasks.get(i).getPos(), "building "+ buildTasks.get(i).getBuilding().getHumanName());
//                }
                break;
            }
            long time2 = System.currentTimeMillis();
            if (time2 - time > 100) {
                if (PLANNING > MIN_PLANNING){
                    PLANNING -= 3;
                }else{
                    HEURISTIC += 0.1;
                }
                command.debug("BuilderHandler took " + (time2 - time) + "ms, planning, heuristic is now " + PLANNING + ", "+ HEURISTIC);
            } else {
                if (HEURISTIC > 0) {
                    HEURISTIC -= 0.05;
                } else {
                    PLANNING++;
                }
            }
            PLANNING = Math.min(Math.max(PLANNING, MIN_PLANNING), MAX_PLANNING);
            HEURISTIC = Math.min(Math.max(HEURISTIC, 0f), MAX_HEURISTIC);
        }
    }

    @Override
    public void unitFinished(AIUnit u) {
        if (u.getMakesEnergy() > 0) {
            energyIncome += u.getMakesEnergy();
            command.debug("Energy income is now: " + energyIncome);
        }
        if (u.getDef().getCustomParams().containsKey("pylonrange")) {
            GridNode gridNode = new GridNode(u.getUnit());
            gridNodes.add(gridNode);
            unitGridNodeFinder.put(u.getUnit().getUnitId(), gridNode);
            //command.mark(u.getPos(), "added grid");
        }
    }

    protected static float distance(AIFloat3 a, AIFloat3 b) {
        AIFloat3 delta = new AIFloat3(a);
        delta.sub(b);
        return delta.length();
    }

    static private int nodeIdCounter = 0;

    @Override
    public void areaZoneChange(Area area, ZoneManager.Zone prev, ZoneManager.Zone next) {
        if (next == ZoneManager.Zone.own){
            for (Mex m : area.getMexes()){
                if (m.getOwner() == ZoneManager.Owner.none){
                }
            }
        }
        if (prev == ZoneManager.Zone.own){
            for (Mex m : area.getMexes()){
                if (m.getBuildTask() != null){
                    energyConstructions.remove(m.getBuildTask());
                    gridNodes.remove(taskGridNodeFinder.get(m.getBuildTask().getTaskId()));
                    taskGridNodeFinder.remove(m.getBuildTask().getTaskId());
                }
            }
        }
    }
        
    public class GridNode implements Comparable {

        public final AIFloat3 pos;
        public final UnitDef building;
        public final float energy;
        public final float range;
        final int nodeId = nodeIdCounter++;
        

        public GridNode(BuildTask bt) {
            pos = bt.getPos();
            building = bt.getBuilding();
            energy = building.getCustomParams().containsKey("income_energy") ? Float.valueOf(building.getCustomParams().get("income_energy")) : 0f;
            range = Float.valueOf(building.getCustomParams().get("pylonrange"));
        }

        public GridNode(Unit u) {
            pos = u.getPos();
            building = u.getDef();
            energy = building.getCustomParams().containsKey("income_energy") ? Float.valueOf(building.getCustomParams().get("income_energy")) : 0f;
            range = Float.valueOf(building.getCustomParams().get("pylonrange"));
        }

        public float distanceTo(GridNode g2) {
            AIFloat3 delta = new AIFloat3(pos);
            delta.sub(g2.pos);
            delta.y = 0;
            return delta.length() - this.range - g2.range - (g2.hashCode() + hashCode()) / 1000f;
        }

        @Override
        public boolean equals(Object t){
            if (t instanceof GridNode) {
                return ((GridNode) t).nodeId == this.nodeId;
//                if (((GridNode) t).pos.x != pos.x) {
//                    return (int) ((((GridNode) t).pos.x - pos.x) * 1000); //should work most of the time
//                }
//                if (((GridNode) t).pos.x != pos.x) {
//                    return (int) ((((GridNode) t).pos.z - pos.z) * 1000);
//                }
//                if (((GridNode) t).pos.y != pos.y) {
//                    return (int) ((((GridNode) t).pos.y - pos.y) * 1000);
//                }
            }
            return false;
        }

        @Override
        public int hashCode() {
            return nodeId;
        }
        
        @Override
        public int compareTo(Object t) {
            if (t instanceof GridNode) {
                return ((GridNode) t).nodeId - this.nodeId;
//                if (((GridNode) t).pos.x != pos.x) {
//                    return (int) ((((GridNode) t).pos.x - pos.x) * 1000); //should work most of the time
//                }
//                if (((GridNode) t).pos.x != pos.x) {
//                    return (int) ((((GridNode) t).pos.z - pos.z) * 1000);
//                }
//                if (((GridNode) t).pos.y != pos.y) {
//                    return (int) ((((GridNode) t).pos.y - pos.y) * 1000);
//                }
            }
            return -1;
        }
    }
}

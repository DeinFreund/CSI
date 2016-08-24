package zkcbai.unitHandlers;

import com.springrts.ai.oo.AIFloat3;
import com.springrts.ai.oo.clb.OOAICallback;
import com.springrts.ai.oo.clb.Team;
import com.springrts.ai.oo.clb.Unit;
import com.springrts.ai.oo.clb.UnitDef;
import com.springrts.ai.oo.clb.WeaponMount;
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
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import zkcbai.Command;
import zkcbai.UnitFinishedListener;
import zkcbai.UpdateListener;
import zkcbai.helpers.AreaChecker;
import zkcbai.helpers.AreaZoneChangeListener;
import zkcbai.helpers.EconomyManager.Budget;
import zkcbai.helpers.PositionChecker;
import zkcbai.helpers.ZoneManager;
import zkcbai.helpers.ZoneManager.Area;
import zkcbai.helpers.ZoneManager.Mex;
import zkcbai.helpers.ZoneManager.Zone;
import zkcbai.unitHandlers.FactoryHandler.Factory;
import zkcbai.unitHandlers.units.AISquad;
import zkcbai.unitHandlers.units.AITroop;
import zkcbai.unitHandlers.units.AIUnit;
import zkcbai.unitHandlers.units.Enemy;
import zkcbai.unitHandlers.units.tasks.BuildTask;
import zkcbai.unitHandlers.units.tasks.MoveTask;
import zkcbai.unitHandlers.units.tasks.ReclaimTask;
import zkcbai.unitHandlers.units.tasks.RepairTask;
import zkcbai.unitHandlers.units.tasks.Task;
import zkcbai.utility.Pair;

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
    protected float energyIncome = 6;
    protected int fusions = 0;
    protected int storages = 0;

    protected final int MAX_PLANNING = 13;
    protected final int MIN_PLANNING = 1;
    protected final int MIN_PYLON_ENERGY_INCOME = 200;
    protected int PLANNING = MAX_PLANNING; //How many steps the grid should be planned ahead
    protected float HEURISTIC = 0;
    protected float MAX_HEURISTIC = 0.7f;
    protected Semaphore planningGridMutex = new Semaphore(1);

    protected final UnitDef llt;
    protected final UnitDef defender;

    protected Set<BuildTask> constructions = new HashSet<>();

    protected Map<RepairTask, AIUnit> repairTasks = new HashMap<>();
    protected Map<AIUnit, RepairTask> unitRepairTaskFinder = new HashMap<>();

    protected Map<GridNode, Map<Float, GridNode>> connections = new HashMap();

    protected Map<Integer, GridNode> unitGridNodeFinder = new HashMap<>();
    protected Map<Integer, GridNode> taskGridNodeFinder = new HashMap<>();
    protected Map<Integer, GridNode> unitDefenseNodeFinder = new HashMap<>();
    protected Map<Integer, GridNode> taskDefenseNodeFinder = new HashMap<>();

    protected Queue<Pair<GridNode, BuildTask>> planQueue = new LinkedBlockingQueue<>();

    protected Set<GridNode> defenseNodes = new HashSet<>();
    protected Set<GridNode> gridNodes = new HashSet<GridNode>() {
        @Override
        public boolean add(GridNode gn) {
            Map<Float, GridNode> newconns = new TreeMap();
            for (GridNode n : gridNodes) {
                connections.get(n).put(n.distanceTo(gn), gn);
                newconns.put(n.distanceTo(gn), n);
            }

            connections.put(gn, newconns);
            return super.add(gn);
        }

        @Override
        public boolean remove(Object gn2) {
            if (gn2 == null) {
                return false;
            }
            if (!(gn2 instanceof GridNode)) {
                throw new RuntimeException("you're doing it wrong!");
            }
            GridNode gn = (GridNode) gn2;
            boolean retval = super.remove(gn);
            for (GridNode n : gridNodes) {
                connections.get(n).remove(n.distanceTo(gn));
            }
            connections.remove(gn);
            return retval;
        }

        @Override
        public boolean removeAll(Collection<?> obj) {
            boolean ret = false;
            for (Object o : obj) {
                if (remove(o)) {
                    ret = true;
                }
            }
            return ret;
        }
    };

    public BuilderHandler(Command cmd, OOAICallback clbk) {
        super(cmd, clbk);
        cmd.addUpdateListener(this);
        cmd.addUnitFinishedListener(this);
        cmd.areaManager.addAreaZoneChangeListener(this);
        llt = command.getCallback().getUnitDefByName("corllt");
        defender = command.getCallback().getUnitDefByName("corrl");
        if (llt == null) {
            throw new AssertionError("llt not found!");
        }
    }

    @Override
    public AIUnit addUnit(Unit u) {
        AIUnit au = new AIUnit(u, this);
        aiunits.put(u.getUnitId(), au);
        return au;
    }

    protected float getTargetEnergyIncome() {
        return (command.getCurrentFrame() / (60f * 30f * 15f) + 1.1f) * avgMetalIncome;
    }

    @Override
    public void troopIdle(AIUnit u) {
        long time1 = System.nanoTime();
        Task best = null;
        float bestscore = -1e9f;
        Set<Area> reachable = u.getReachableAreas();
        for (BuildTask bt : constructions) {
            //command.mark(bt.getPos(), bt.getBuilding().getHumanName());
            if (bt.isDone() || bt.isAborted()) {
                throw new RuntimeException("Didn't clean task from constructions");
            }
            if (command.areaManager.getArea(bt.getPos()).getZone() != Zone.own) {
                continue;
            }
            if (!reachable.contains(command.areaManager.getArea(bt.getPos()))) {
                continue;
            }

            float score = -u.distanceTo(bt.getPos()) - (bt.getWorkers().size() + ((bt.getBuilding().getSpeed() > 0.1) ? 2.5f : 1f)) * 3000 * 100 / bt.getBuilding().getCost(command.metal) - command.areaManager.getArea(bt.getPos()).getDanger() * 0.1000f;

            if (u.getMetalCost() > 500 && bt.getBuilding().isAbleToAttack() && energyIncome > 10) {
                //compush
                score += 4500;
            }
            if (bt.getBuilding().getName().equals("cormex")) {
                score += 2500;
            }
            if (bt.getBuilding().getName().equals("armsolar") && avgMetalIncome > energyIncome) {
                score += 3000;
            }
            if (best == null || score > bestscore) {
                bestscore = score;
                best = bt;
            }
        }
        long time2 = System.nanoTime();
        for (RepairTask rt : repairTasks.keySet()) {
            if (!reachable.contains(command.areaManager.getArea(rt.getTarget().getPos()))) {
                continue;
            }
            if (command.areaManager.getArea(rt.getTarget().getPos()).getZone() != Zone.own) {
                continue;
            }
            if (u.equals(rt.getTarget())) {
                continue;
            }
            float score = -u.distanceTo(rt.getTarget().getPos()) / 3f - rt.getWorkers().size() * 500 - command.areaManager.getArea(rt.getTarget().getPos()).getDanger() * 0.1000f;

            if (best == null || score > bestscore) {
                bestscore = score;
                best = rt;
            }
        }

        long time3 = System.nanoTime();
        for (Area a : command.areaManager.getAreasWithReclaim()) {
            if (!reachable.contains(a) || a.getZone() == Zone.hostile) {
                continue;
            }
            if (a.getReclaim() < 10) {
                continue;
            }
            float score = -u.distanceTo(a.getPos()) + a.getReclaim() - 100;
            if (best == null || score > bestscore) {
                bestscore = score;
                best = new ReclaimTask(a.getPos(), Math.max(a.getWidth(), a.getHeight()), this, command);
            }
        }

        long time4 = System.nanoTime();
        if (best != null) {
            best.setInfo(best.getInfo() + " - queued by BuilderHandler");
            u.queueTask(best);
        } else {
            u.assignTask(new MoveTask(u.getArea().getNearestArea(new AreaChecker() {

                @Override
                public boolean checkArea(Area a) {
                    return a.isReachable() && a.getZone() == Zone.own;
                }
            }).getPos(), command.getCurrentFrame() + 60, this, command));
        }
        long time5 = System.nanoTime();
        if (time5 - time1 > 500000) {
            command.debug(command.areaManager.getAreasWithReclaim().size() + " areas with reclaim");
            command.debug(constructions.size() + " constructions");
            command.debug("builder assignment timing: " + (time5 - time4) + " | " + (time4 - time3) + " | " + (time3 - time2) + " | " + (time2 - time1));
        }
    }

    public Collection<AIUnit> getBuilders() {
        return aiunits.values();
    }

    /**
     * Registers a BuildTask so constructors will assist it, don't forget to
     * unregister the buildTask on completion
     *
     * @param bt
     */
    public void registerBuildTask(BuildTask bt) {
        constructions.add(bt);
    }

    /**
     * Removes a BuildTask from the store
     *
     * @param bt
     * @return whether the BuildTask was actually registered
     */
    public boolean unregisterBuildTask(BuildTask bt) {
        return constructions.remove(bt);
    }

    protected void endedTask(final Task t) {

        if (t instanceof BuildTask) {

            //command.mark(((BuildTask)t).getPos(), "removed grid");
            boolean known = constructions.remove((BuildTask) t);
            if (taskGridNodeFinder.containsKey(t.getTaskId())) {
                command.addSingleUpdateListener(new UpdateListener() {

                    @Override
                    public void update(int frame) {

                        gridNodes.remove(taskGridNodeFinder.get(t.getTaskId()));
                        taskGridNodeFinder.remove(t.getTaskId());
                    }
                }, command.getCurrentFrame() + 10);
            } else if (taskDefenseNodeFinder.containsKey(t.getTaskId())) {
                command.addSingleUpdateListener(new UpdateListener() {

                    @Override
                    public void update(int frame) {

                        defenseNodes.remove(taskDefenseNodeFinder.get(t.getTaskId()));
                        taskDefenseNodeFinder.remove(t.getTaskId());
                    }
                }, command.getCurrentFrame() + 10);
            } else if (t.getInfo().contains("radar")) {
                command.addSingleUpdateListener(new UpdateListener() {

                    @Override
                    public void update(int frame) {

                        buildingRadar = false;
                    }
                }, command.getCurrentFrame() + 10);
            } else if (!(t.getInfo().contains("nano") || t.getInfo().contains("mex") || t.getInfo().contains("radar") || t.getInfo().contains("storage"))) {
                throw new AssertionError("Unknown task " + ((BuildTask) t).getBuilding().getHumanName() + " info: " + t.getInfo() + " known: " + known + " id: " + t.getTaskId());
            }
        } else if (t instanceof RepairTask) {
            RepairTask rt = (RepairTask) t;
            repairTasks.remove(rt);
            unitRepairTaskFinder.remove(rt.getTarget());
        }
    }

    @Override
    public void abortedTask(Task t) {
        endedTask(t);
        if (t instanceof BuildTask && ((BuildTask) t).getBuilding().getName().equals("armfus")) {
            fusions--;
        }
        if (t instanceof BuildTask && ((BuildTask) t).getBuilding().getName().equals("armmstor")) {
            storages--;
        }

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
        if (unitDefenseNodeFinder.containsKey(u.getUnit().getUnitId())) {
            defenseNodes.remove(unitDefenseNodeFinder.get(u.getUnit().getUnitId()));
            unitDefenseNodeFinder.remove(u.getUnit().getUnitId());
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

                if (dist > best.get(node) || !gridNodes.contains(node)) {
                    //if (dists.isEmpty()) command.debug("ERROR: node was never added to dists");
                    continue;
                }

                //unconnected.remove(node);
                if (prev.get(node).distanceTo(node) > 0) {
                    unbuiltEdges.add(new Edge(prev.get(node), node));
                    unbuiltEdges.add(new Edge(node, prev.get(node)));
                    //command.getCallback().getMap().getDrawer().addLine(prev.get(node).pos, node.pos);
                }

                int max = (int) Math.round((1 - HEURISTIC) * connections.get(node).size());
                int i = 0;
                for (GridNode to : connections.get(node).values()) {
                    if (++i >= max) {
                        break;
                    }
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
        final UnitDef[] eBuildings;
        if (energyIncome > avgMetalIncome * 1.5) {
            eBuildings = new UnitDef[]{command.getCallback().getUnitDefByName("armsolar"),
                command.getCallback().getUnitDefByName("armestor"), /*command.getCallback().getUnitDefByName("armwin")*/};
        } else {

            eBuildings = new UnitDef[]{command.getCallback().getUnitDefByName("armsolar"), /*command.getCallback().getUnitDefByName("armwin")*/};
        }

        final float searchRadiusMult = 0.4f;
        for (final Edge edge : unbuiltEdges) { //each edge is contained twice, so only attaching to start has to be evalued
            for (final UnitDef building : eBuildings) {
                final float buildingRange = Float.valueOf(building.getCustomParams().get("pylonrange"));
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
                    if (energyIncome > MIN_PYLON_ENERGY_INCOME) {
                        cost *= 0.5;
                    } else {
                        cost *= 2;
                    }
                }
                final AIFloat3 pos = new AIFloat3(edge.start.pos);
                if (edge.end.distanceTo(edge.start) < 0.92 * buildingRange) {
                    //Try to build in mid
                    pos.add(edge.end.pos);
                    pos.scale(0.5f);
                    if (!command.isPossibleToBuildAt(building, pos, 0)) {
                        pos.set(BuildTask.findClosestBuildSite(building, pos, 3, 0, command, true));
                    }

                    if (command.isPossibleToBuildAt(building, pos, 0)
                            && Command.distance2D(pos, edge.start.pos) < 0.95 * (edge.start.range + buildingRange)
                            && Command.distance2D(pos, edge.end.pos) < 0.95 * (edge.end.range + buildingRange)
                            && command.areaManager.getArea(pos).isReachable()) {
                        //command.mark(pos, "midposs");
                        possibilities.add(new BuildPossibility(building, pos, cost * 0.65f));
                    }
                }
                //try to build on line
                pos.set(dir);
                pos.scale(0.92f * (edge.start.range + buildingRange));
                pos.add(edge.start.pos);
                if (command.isPossibleToBuildAt(building, pos, 0) && command.areaManager.getArea(pos).isReachable()) {
                    possibilities.add(new BuildPossibility(building, pos, cost));
                } else {
                    //try to build near start
                    pos.set(dir);
                    pos.scale(0.92f * (edge.start.range + buildingRange));
                    pos.add(edge.start.pos);

                    AIFloat3 bpos = BuildTask.findClosestBuildSite(building, pos, 3, 0, command, -1f, true, new PositionChecker() {
                        @Override
                        public boolean checkPosition(AIFloat3 pos) {
                            return Command.distance2D(pos, edge.start.pos) < 0.92 * (edge.start.range + buildingRange);
                        }
                    }, edge.start.range + buildingRange);
                    if (bpos.x < 0) {
                        continue;
                    }
                    float efficiency = distance(bpos, edge.start.pos) / (edge.start.range + buildingRange);
                    if (pos.x > 0 && distance(bpos, edge.start.pos) < edge.start.range + buildingRange
                            && //has to reduce distance between circles(greedy):
                            distance(edge.start.pos, edge.end.pos) - edge.start.range - edge.end.range > distance(bpos, edge.end.pos) - edge.end.range
                            - buildingRange && command.isPossibleToBuildAt(building, bpos, 0)) {
                        //command.mark(bpos, "possible");
                        possibilities.add(new BuildPossibility(building, bpos, cost / efficiency));
                    }
                }
            }
        }
        long time3 = System.currentTimeMillis();

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
            if (!command.isPossibleToBuildAt(best.building, best.pos, 0)) {
                command.debug("HONK!");
            }
            buildTask = new BuildTask(best.building, best.pos, 0, null, this, clbk, command);
            buildTask.setInfo("new");
            command.registerBuildTask(buildTask);
        } else {
            //command.debug("No build possibility found for energy!");
        }
        long time4 = System.currentTimeMillis();
        if (time3 - time > 50) {
            command.debug("QueueBuildTask took " + (time3 - time2) + "ms selecting from " + unbuiltEdges.size() + " edges, " + (time2 - time) + "ms calculating MST, " + (time4 - time3) + "ms calculating buildpos");
            //command.debugStackTrace();
        }

        return buildTask;
    }

    public Collection<GridNode> getGridNodes() {
        return gridNodes;
    }

    public Collection<GridNode> getDefenseNodes() {
        return defenseNodes;
    }

    public float getEnergyUnderConstruction() {
        float res = 0;
        for (BuildTask bt : constructions) {
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
        for (BuildTask bt : constructions) {
            res += (bt.getBuilding().getName().equalsIgnoreCase("cormex") ? 1.5 : 0f);
        }
        return res;
    }

    protected int lastEnergyCheck = 0;

    private Color generateRandomColor() {
        int red = rnd.nextInt(3) * 255 / 2;
        int green = rnd.nextInt(3) * 255 / 2;
        int blue = rnd.nextInt(3) * 255 / 2;

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
        while (!pq.isEmpty()) {
            GridNode pos = pq.poll();
            if (end.contains(pos)) {
                end.remove(pos);
            }

            Collection<GridNode> toRemove = new ArrayList();
            for (GridNode gn : unvisited) {
                if (gn.distanceTo(pos) < 0) {
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

    public BuildTask requestCaretaker(AIFloat3 pos) {
        BuildTask bt;
        /*if (command.getNanoHandler().getUnits().size() > command.getFactoryHandler().getUnits().size() * 4) {
            bt = new BuildTask(command.getCallback().getUnitDefByName("factoryveh"), new AIFloat3(pos.x + 250, pos.y, pos.z), this, clbk, command, 7, true);
        } else {
         */ bt = new BuildTask(command.getCallback().getUnitDefByName("armnanotc"), pos, Budget.economy, this, clbk, command, 7, true);
        //}
        command.registerBuildTask(bt);
        bt.setInfo("nano");
        constructions.add(bt);
        return bt;
    }

    protected boolean buildingRadar = false;

    public BuildTask requestRadarCoverage(AIFloat3 pos) {
        long time = System.currentTimeMillis();
        if (buildingRadar) {
            return null;
        }
        command.mark(pos, "requested radar");
        /*
         Area area = command.areaManager.getArea(pos).getNearestArea(new AreaChecker() {

         @Override
         public boolean checkArea(Area a) {
         return command.radarManager.isInRadar(a.getPos())
         && command.isPossibleToBuildAt(command.getCallback().getUnitDefByName("corrad"), a.getPos(), 0);
                
         }
         }, Pathfinder.MovementType.air, false);
         if (area == null) {
         if (aiunits.isEmpty()) {
         return null;
         }
         area = command.areaManager.getArea(aiunits.values().iterator().next().getPos()).getNearestArea(new AreaChecker() {

         @Override
         public boolean checkArea(Area a) {
         return command.isPossibleToBuildAt(command.getCallback().getUnitDefByName("corrad"), a.getPos(), 0);
         }
         });
         }*/
        BuildTask bt = new BuildTask(command.getCallback().getUnitDefByName("corrad"), pos, Budget.defense, this, clbk, command, 4, 300, true);
        bt.setInfo("radar");
        command.registerBuildTask(bt);
        constructions.add(bt);
        buildingRadar = true;
        time = System.currentTimeMillis() - time;
        if (time > 20) {
            command.debug("Requesting radar took " + time + "ms.");
        }
        return bt;
    }

    /**
     *
     * @param au unit to be repaired
     * @return
     */
    public RepairTask requestRepairs(AIUnit au) {
        if (!unitRepairTaskFinder.containsKey(au)) {
            RepairTask rt = new RepairTask(au, this, command);
            unitRepairTaskFinder.put(au, rt);
            repairTasks.put(rt, au);
        }
        return unitRepairTaskFinder.get(au);
    }

    protected boolean isArticulationPoint(GridNode gridNode) {
        List<GridNode> neighbours = new ArrayList();
        for (GridNode gn : gridNodes) {
            if (gn.distanceTo(gridNode) < 0 && !gn.equals(gridNode)) {
                neighbours.add(gn);
            }
        }
        if (neighbours.isEmpty()) {
            return true;
        }
        GridNode last = neighbours.get(neighbours.size() - 1);
        neighbours.remove(neighbours.size() - 1);
        return neighbours.isEmpty() || !isConnected(last, new HashSet(neighbours), new HashSet<>(Arrays.asList(gridNode)));
    }

    /**
     * Use Area.getDefenseDPS() instead
     *
     * @param pos
     * @return
     */
    public float _getDefenseDPS(AIFloat3 pos) {
        float res = 0;
        for (GridNode gn : defenseNodes) {
            if (distance(pos, gn.pos) < gn.range) {
                res += gn.energy;
            }
        }
        return res;
    }

    public void buildDefense() {
        long time = System.currentTimeMillis();
        Set<Factory> unprotectedFacs = new HashSet<>();
        for (Factory f : command.getFactoryHandler().getFacs()) {
            boolean safe = false;
            for (GridNode gn : defenseNodes) {
                if (distance(f.unit.getPos(), gn.pos) < gn.range) {
                    safe = true;
                }
            }
            if (!safe) {
                unprotectedFacs.add(f);
            }
        }
        for (Factory fac : unprotectedFacs) {
            BuildTask bt = new BuildTask(llt, fac.unit.getPos(), Budget.defense, this, clbk, command, 5, true);
            GridNode gn = new GridNode(bt);
            bt.setInfo("fac llt");
            command.registerBuildTask(bt);
            constructions.add(bt);
            this.defenseNodes.add(gn);
            taskDefenseNodeFinder.put(bt.getTaskId(), gn);
            return;
        }
        Area worst = null;
        for (Area a : command.areaManager.getAreas()) {
            if (!a.isFront() || !a.isReachable()) {
                continue;
            }
            if (worst == null || (worst.getDefenseDPS() > a.getDefenseDPS() || (a.getDefenseDPS() - worst.getDefenseDPS() < 10 && a.getNegativeFlow() > worst.getNegativeFlow()))) {
                worst = a;
            }
        }
        if (worst != null) {
            BuildTask bt = new BuildTask(defender, worst.getPos(), Budget.defense, this, clbk, command, 5, 400, true);
            GridNode gn = new GridNode(bt);
            bt.setInfo("front defender");
            command.registerBuildTask(bt);
            constructions.add(bt);
            this.defenseNodes.add(gn);
            taskDefenseNodeFinder.put(bt.getTaskId(), gn);
        }
        time = System.currentTimeMillis() - time;
        if (time > 100) {
            command.debug("Planning Defenses took " + time + "ms.");
        }
    }

    public float getMetalIncome() {
        float income = 0;
        for (Team t : clbk.getAllyTeams()) {
            income += clbk.getGame().getTeamResourceIncome(t.getTeamId(), command.metal.getResourceId());
        }
        return income;
    }

    int imgscale = 10;
    Graphics2D gbefore;

    int lastConCheck = 0;
    int lastQueueSize = 0;

    int lastMexCheck = -600;
    int lastDefenseCheck = 100;

    @Override
    public void update(int frame) {
        long _time = System.currentTimeMillis();
        float adaption = 0.05f;
        avgMetalIncome = (1 - adaption) * avgMetalIncome + getMetalIncome() * adaption;

        if (frame - lastConCheck > 300) {
            lastConCheck = frame;
            if ((constructions.size() - lastQueueSize > 0 && constructions.size() > aiunits.size() || aiunits.isEmpty()) && command.getCurrentFrame() > 30 * 60 * 2) {
                command.getFactoryHandler().requestConstructor();
                lastQueueSize = constructions.size();
            }

        }

        if (frame % 50 == 25 && frame > 30 * 60) {
            for (BuildTask bt : constructions.toArray(new BuildTask[constructions.size()])) {
                if (command.areaManager.getArea(bt.getPos()).getZone() == Zone.hostile) {
                    if (bt.getBuilding().getSpeed() > 0) {
                        command.mark(bt.getPos(), "factory in enemy zone: " + command.areaManager.getArea(bt.getPos()).getZone().name());
                    }
                    bt.cancel();
                    constructions.remove(bt);
                    gridNodes.remove(taskGridNodeFinder.get(bt.taskID));
                    taskGridNodeFinder.remove(bt.taskID);
                    defenseNodes.remove(taskDefenseNodeFinder.get(bt.taskID));
                    taskDefenseNodeFinder.remove(bt.taskID);
                }
            }
        }

        //command.debug((avgMetalIncome + getMetalUnderConstruction()) + " < " + (energyIncome + getEnergyUnderConstruction()) + " aiunits: " + aiunits.size());
        if (/*avgMetalIncome + getMetalUnderConstruction() < energyIncome + getEnergyUnderConstruction() &&*/frame - lastMexCheck > 40 && !aiunits.isEmpty()) {
            long time = System.currentTimeMillis();
            lastMexCheck = frame;
            float toBuild = energyIncome + getEnergyUnderConstruction() - avgMetalIncome - getMetalUnderConstruction();
            toBuild = 10000;
            TreeMap<Float, Mex> dists = new TreeMap<>();
            for (Mex m : command.areaManager.getMexes()) {
                float mindist = 1000000;
                for (Factory f : command.getFactoryHandler().getFacs()) {
                    mindist = Math.min(mindist, f.unit.distanceTo(m.pos));
                }
                dists.put(mindist, m);
            }
            for (Mex m : dists.values()) {
                if (m.getBuildTask() != null /*|| command.areaManager.getArea(m.pos).getZone() != Zone.own*/) {
                    //command.mark(m.pos, "invalid");
                    continue;
                }
                if (!command.radarManager.isInRadar(m.pos)) {
                    //if (constructions.size() < aiunits.size() * 0.6) {
                    requestRadarCoverage(m.pos);
                    //}
                    //continue;
                }
                if (command.areaManager.getArea(m.pos).getZone() != Zone.own) {
                    continue;
                }
                if (command.economyManager.getRemainingBudget(Budget.economy) < 0) {
                    break;
                }
                BuildTask bt = m.createBuildTask(this);
                if (bt == null) {
                    //command.mark(m.pos, "Can't build mex here");
                    continue;
                }
                toBuild -= m.getIncome();
                GridNode gn = new GridNode(bt);
                bt.setInfo("mextask");
                constructions.add(bt);
                gridNodes.add(gn);
                taskGridNodeFinder.put(bt.getTaskId(), gn);
                if (toBuild < 0) {
                    break;
                }
            }
            time = System.currentTimeMillis() - time;
            if (time > 100) {
                command.debug("Planning mextasks took " + time + " ms.");
            }
        }
        if (frame - lastDefenseCheck > 60 && command.economyManager.getRemainingBudget(Budget.defense) > 100) {
            buildDefense();
            lastDefenseCheck = frame;
        }

        if ((getTargetEnergyIncome() > energyIncome + getEnergyUnderConstruction() || command.getCallback().getEconomy().getCurrent(command.energy) < 90)
                && frame - lastEnergyCheck > 60 && command.economyManager.getRemainingBudget(Budget.economy) > 100 && planningGridMutex.tryAcquire()) {

            //command.debug("Planning grid async.");
            //Thread planningThread = new Thread(new Runnable() {
            //    public void run() {
            planGrid();
            //    }
            //});
            //planningThread.start();

        }
        for (Pair<GridNode, BuildTask> p : planQueue) {
            if (command.economyManager.getRemainingBudget(Budget.economy) < 0) {
                break;
            }
            constructions.add(p.getSecond());
            this.gridNodes.add(p.getFirst());
            taskGridNodeFinder.put(p.getSecond().getTaskId(), p.getFirst());
            command.economyManager.useBudget(Budget.economy, p.getSecond().getBuilding().getCost(command.metal));
        }
        planQueue.clear();
        if (energyIncome > fusions * 90 + 40) {
            fusions++;
            BuildTask bt = new BuildTask(clbk.getUnitDefByName("armfus"), command.getFactoryHandler().getFacs().iterator().next().unit.getPos(), Budget.economy, this, clbk, command, 5, true);
            GridNode gn = new GridNode(bt);
            command.registerBuildTask(bt);
            constructions.add(bt);
            this.gridNodes.add(gn);
            taskGridNodeFinder.put(bt.getTaskId(), gn);
        }
        if (avgMetalIncome > storages * 15 + 20 && command.economyManager.getRemainingBudget(Budget.economy) > 0) {
            storages++;
            BuildTask bt = new BuildTask(clbk.getUnitDefByName("armmstor"), command.getFactoryHandler().getFacs().toArray(new Factory[command.getFactoryHandler().getFacs().size()])[(int) (Math.random() * command.getFactoryHandler().getFacs().size())].unit.getPos(), Budget.economy, this, clbk, command, 5, true);
            bt.setInfo("storage");
            command.registerBuildTask(bt);
            constructions.add(bt);
        }
        //reassign workers
        if (frame % 33 == 2) {
            long time = System.currentTimeMillis();
            for (AIUnit au : aiunits.values()) {
                Task t = au.getTask();
                if (t != null) {
                    while (!t.getQueue().isEmpty()) {
                        t = t.getQueue().get(t.getQueue().size() - 1);
                    }

                    switch (t.getTaskType()) {
                        case BuildTask:
                            BuildTask bt = (BuildTask) t;
                            bt.removeWorker(au);
                            break;
                        case RepairTask:
                            RepairTask rt = (RepairTask) t;
                            rt.removeWorker(au);
                            break;
                    }
                }
                troopIdle(au);
            }

            time = System.currentTimeMillis() - time;
            if (time > 5) {
                command.debug("Reassigning " + aiunits.size() + " workers took " + time + "ms.");
            }
        }
    }

    public float getMetalStorage() {
        return storages * 500 + 500;
    }

    public void planGrid() {
        long time = System.currentTimeMillis();
        lastEnergyCheck = command.getCurrentFrame();
        //BufferedImage before = new BufferedImage(mwidth/ imgscale, mheight/imgscale, BufferedImage.TYPE_INT_RGB);
        //gbefore =  before.createGraphics();

        List<BuildTask> buildTasks = new ArrayList();
        List<GridNode> gridNodes = new ArrayList();
        int planning = PLANNING;
        boolean[] useless = new boolean[planning];
        for (int i = 0; i < planning; i++) {
            buildTasks.add(queueEnergyBuilding());
            if (buildTasks.get(i) == null) {
                planning = i;
                //command.debug("Only planning grid " + i + " steps ahead.", false);
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
        for (int i = 0; i < planning; i++) {
            if (useless[i]) {
                buildTasks.get(i).cancel();
                continue;
            }
            buildTasks.get(i).setInfo("gridplan");
            planQueue.add(new Pair(gridNodes.get(i), buildTasks.get(i)));
            for (int ii = i; ii < planning; ii++) {
                useless[ii] = true;
            }
        }
        long time2 = System.currentTimeMillis();
        if (time2 - time > 100) {
            if (PLANNING > MIN_PLANNING) {
                PLANNING -= 3;
            } else {
                HEURISTIC += 0.1;
            }
        } else if (HEURISTIC > 0) {
            HEURISTIC -= 0.05;
        } else {
            PLANNING++;
        }
        PLANNING = Math.min(Math.max(PLANNING, MIN_PLANNING), MAX_PLANNING);
        HEURISTIC = Math.min(Math.max(HEURISTIC, 0f), MAX_HEURISTIC);
        if (time2 - time > 80) {
            command.debug("BuilderHandler took " + (time2 - time) + "ms, planning, heuristic is now " + PLANNING + ", " + HEURISTIC);
        }
        planningGridMutex.release();
    }

    @Override
    public void unitFinished(AIUnit u) {
        if (u.getMakesEnergy() > 0) {
            energyIncome += u.getMakesEnergy();
            //command.debug("Energy income is now: " + energyIncome);
        }
        if (u.getDef().getCustomParams().containsKey("pylonrange")) {
            GridNode gridNode = new GridNode(u.getUnit());
            gridNodes.add(gridNode);
            unitGridNodeFinder.put(u.getUnit().getUnitId(), gridNode);
            //command.mark(u.getPos(), "added grid");
        }
        if (u.getDef().isAbleToAttack() && u.getDef().getBuildOptions().isEmpty() && u.getDef().getSpeed() <= 0.01) {
            GridNode gridNode = new GridNode(u.getUnit());
            defenseNodes.add(gridNode);
            unitDefenseNodeFinder.put(u.getUnit().getUnitId(), gridNode);
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
        if (next == ZoneManager.Zone.own) {
            for (Mex m : area.getMexes()) {
                if (m.getOwner() == ZoneManager.Owner.none) {
                }
            }
        }
        if (prev == ZoneManager.Zone.own) {
            for (Mex m : area.getMexes()) {
                if (m.getBuildTask() != null) {
                    constructions.remove(m.getBuildTask());
                    gridNodes.remove(taskGridNodeFinder.get(m.getBuildTask().getTaskId()));
                    taskGridNodeFinder.remove(m.getBuildTask().getTaskId());
                }
            }
        }
    }

    @Override
    public boolean retreatForRepairs(AITroop u) {
        return true;

    }

    public class GridNode implements Comparable {

        public final AIFloat3 pos;
        public final UnitDef building;
        public final float energy;
        public final float dps;
        public final float range;
        final int nodeId = nodeIdCounter++;

        public GridNode(BuildTask bt) {
            pos = bt.getPos();
            building = bt.getBuilding();
            energy = building.getCustomParams().containsKey("income_energy") ? Float.valueOf(building.getCustomParams().get("income_energy")) : 0f;
            if (bt.getBuilding().isAbleToAttack()) {
                float max = 0;
                float dmg = 0;
                for (WeaponMount wm : bt.getBuilding().getWeaponMounts()) {
                    max = Math.max(max, wm.getWeaponDef().getRange());
                    for (Float f : wm.getWeaponDef().getDamage().getTypes()) {
                        dmg += f / wm.getWeaponDef().getReload();
                    }
                }
                this.dps = dmg;
                range = max;
            } else {
                this.dps = 0;
                range = Float.valueOf(building.getCustomParams().get("pylonrange"));
            }
        }

        public GridNode(Unit u) {
            pos = u.getPos();
            building = u.getDef();
            energy = building.getCustomParams().containsKey("income_energy") ? Float.valueOf(building.getCustomParams().get("income_energy")) : 0f;

            if (u.getDef().isAbleToAttack()) {
                range = u.getMaxRange();
                float dmg = 0;
                for (WeaponMount wm : u.getDef().getWeaponMounts()) {
                    for (Float f : wm.getWeaponDef().getDamage().getTypes()) {
                        dmg += f / wm.getWeaponDef().getReload();
                    }
                }
                dps = dmg;
            } else {
                range = Float.valueOf(building.getCustomParams().get("pylonrange"));
                dps = 0;
            }
        }

        public float distanceTo(GridNode g2) {
            AIFloat3 delta = new AIFloat3(pos);
            delta.sub(g2.pos);
            delta.y = 0;
            return delta.length() - this.range - g2.range - (g2.hashCode() + hashCode()) / 1000f;
        }

        @Override
        public boolean equals(Object t) {
            if (t instanceof GridNode) {
                return ((GridNode) t).nodeId == this.nodeId;
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
            }
            return -1;
        }
    }
}

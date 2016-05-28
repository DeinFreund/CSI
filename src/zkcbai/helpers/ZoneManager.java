/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package zkcbai.helpers;

import com.springrts.ai.oo.AIFloat3;
import com.springrts.ai.oo.clb.OOAICallback;
import com.springrts.ai.oo.clb.Team;
import com.springrts.ai.oo.clb.Unit;
import com.springrts.ai.oo.clb.UnitDef;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.JPanel;
import zkcbai.Command;
import zkcbai.UnitDestroyedListener;
import zkcbai.UnitFinishedListener;
import zkcbai.UpdateListener;
import static zkcbai.helpers.Helper.command;
import zkcbai.helpers.Pathfinder.MovementType;
import zkcbai.helpers.ZoneManager.Area.Connection;
import zkcbai.helpers.ZoneManager.Area.DebugConnection;
import zkcbai.helpers.ZoneManager.Owner;
import static zkcbai.helpers.ZoneManager.Owner.enemy;
import static zkcbai.helpers.ZoneManager.Owner.none;
import zkcbai.helpers.ZoneManager.Zone;
import static zkcbai.helpers.ZoneManager.Zone.fortified;
import static zkcbai.helpers.ZoneManager.Zone.hostile;
import static zkcbai.helpers.ZoneManager.Zone.neutral;
import static zkcbai.helpers.ZoneManager.Zone.own;
import zkcbai.unitHandlers.BuilderHandler.GridNode;
import zkcbai.unitHandlers.FactoryHandler.Factory;
import zkcbai.unitHandlers.units.AIUnit;
import zkcbai.unitHandlers.units.Enemy;
import zkcbai.unitHandlers.units.FakeCommander;
import zkcbai.unitHandlers.units.FakeMex;
import zkcbai.unitHandlers.units.tasks.BuildTask;
import zkcbai.unitHandlers.units.tasks.TaskIssuer;

/**
 *
 * @author User
 */
public class ZoneManager extends Helper implements UnitDestroyedListener {

    private static final boolean GUI = false;

    private Area[][] map;
    private List<Area> areas;//for simplified iterating

    private JFrame frm;
    private MapGUI pnl;

    private List<Mex> mexes;

    private final int mwidth;
    private final int mheight;

    private Random random = new Random();

    private Set<AreaZoneChangeListener> zoneChangeListeners = new HashSet();

    public enum Zone {

        own, fortified, hostile, neutral
    }

    public enum Owner {

        own, none, enemy
    }

    public final AreaChecker NOT_IN_LOS = new AreaChecker() {
        @Override
        public boolean checkArea(Area a) {
            return !a.isVisible();
        }
    };
    public final AreaChecker HOSTILE = new AreaChecker() {
        @Override
        public boolean checkArea(Area a) {
            return a.getZone() == Zone.hostile;
        }
    };
    public final AreaChecker HOSTILE_RAIDER_ACCESSIBLE = new AreaChecker() {
        @Override
        public boolean checkArea(Area a) {
            return HOSTILE.checkArea(a) && RAIDER_ACCESSIBLE.checkArea(a);
        }
    };
    public final AreaChecker FORTIFIED = new AreaChecker() {
        @Override
        public boolean checkArea(Area a) {
            return a.getZone() == Zone.fortified;
        }
    };
    public final AreaChecker FORTIFIED_ASSAULT_ACCESSIBLE = new AreaChecker() {
        @Override
        public boolean checkArea(Area a) {
            return FORTIFIED.checkArea(a) && ASSAULT_ACCESSIBLE.checkArea(a);
        }
    };
    public final AreaChecker RAIDER_ACCESSIBLE = new AreaChecker() {
        @Override
        public boolean checkArea(Area a) {
            return command.defenseManager.isRaiderAccessible(a.getPos())
                    && command.pathfinder.isReachable(a.getPos(), command.getStartPos(), clbk.getUnitDefByName("armpw").getMoveData().getMaxSlope());
        }
    };
    public final AreaChecker ASSAULT_ACCESSIBLE = new AreaChecker() {
        @Override
        public boolean checkArea(Area a) {
            return command.defenseManager.isAssaultAccessible(a.getPos())
                    && command.pathfinder.isReachable(a.getPos(), command.getStartPos(), clbk.getUnitDefByName("armzeus").getMoveData().getMaxSlope());
        }
    };

    public int getMapHeight() {
        return mheight;
    }

    public int getMapWidth() {
        return mwidth;
    }

    public ZoneManager(Command cmd, OOAICallback clbk) {
        super(cmd, clbk);
        mwidth = clbk.getMap().getWidth() * 8;
        mheight = clbk.getMap().getHeight() * 8;
        map = new Area[50][50];
        areas = new ArrayList();
        int arindex = 0;
        BufferedImage csiimg = new BufferedImage(50, 50, BufferedImage.TYPE_3BYTE_BGR);
        try {
            csiimg = ImageIO.read(Command.class.getResource("/zkcbai/resources/csi.png"));
            command.debug("read csi image " + csiimg.getWidth() + "|" + csiimg.getHeight());
        } catch (IOException ex) {
            command.debug("failed to read csi image", ex);
        }
        for (int x = 0; x < map.length; x++) {
            for (int y = 0; y < map[x].length; y++) {
                map[x][y] = new Area(x, y, arindex++);
                areas.add(map[x][y]);

            }
        }
        for (int x = 0; x < map.length; x++) {
            for (int y = 0; y < map[x].length; y++) {
                if (!new Color(csiimg.getRGB(x, y)).equals(Color.white)) {
                    map[x][y].setReachable(true);
                    if (y > 0) {
                        map[x][y].addConnection(map[x][y - 1], -1, MovementType.vehicle);
                    }
                    if (y < map[0].length - 1) {
                        map[x][y].addConnection(map[x][y + 1], -1, MovementType.vehicle);
                    }
                    if (x > 0) {
                        map[x][y].addConnection(map[x - 1][y], -1, MovementType.vehicle);
                    }
                    if (x < map.length - 1) {
                        map[x][y].addConnection(map[x + 1][y], -1, MovementType.vehicle);
                    }
                }
            }
        }
        mexes = new ArrayList();
        Collections.shuffle(areas);
        Collections.shuffle(mexes);
        cmd.addUnitDestroyedListener(this);

        if (GUI) {
            frm = new JFrame("MapGUI");
            pnl = new MapGUI();
            frm.add(pnl);
            frm.setVisible(true);
            frm.setSize(500, 500 * clbk.getMap().getHeight() / clbk.getMap().getWidth());
        }
    }

    @Override
    public void init() {

        command.debug("Zone Manager precalculating paths...");
        int i = 0;
        for (Area a : areas) {
            a.recalculatePaths();
        }
        PathPrecalculatorThread.calculate(command, pnl);
        for (Area a : areas) {
            a.setReachable(false);
        }
        command.debug("Zone Manager post-initialized");
    }

    public void addAreaZoneChangeListener(AreaZoneChangeListener listener) {
        zoneChangeListeners.add(listener);
    }

    public void removeAreaZoneChangeListener(AreaZoneChangeListener listener) {
        zoneChangeListeners.remove(listener);
    }

    private void parseStartScript() {
        AIFloat3 compos = null;
        try {
            String script = clbk.getGame().getSetupScript();
            //command.debug("startscript:\n" + script);
            List<List<Float>> startboxes = new ArrayList();
            for (String line : script.split("\n")) {
                if (line.contains("startboxes")) {
                    //command.debug("line: " + line);
                    //command.debug("stripped: "+ line.substring(line.indexOf("{") + 1, line.lastIndexOf("}") - 1));
                    for (String element : line.substring(line.indexOf("{") + 1, line.lastIndexOf("}") - 1).split("},")) {
                        try {
                            List<Float> startbox = new ArrayList();
                            //command.debug("Element: " + element);
                            //command.debug((element.indexOf("{") + 1)+ ", "+ (element.lastIndexOf("}") - 1));
                            if (element.length() > 5) {
                                for (String num : element.substring(element.indexOf("{") + 1).split(",")) {
                                    startbox.add(Float.parseFloat(num));
                                }
                            }
                            startboxes.add(startbox);
                        } catch (Exception ex) {
                            command.debug("Exception while parsing startbox: ", ex);
                        }
                    }

                }
            }

            command.debug("TeamRulesParams:");
            for (Team t : clbk.getAllyTeams()) {
                //for (TeamRulesParam trp : t.getTeamRulesParams()) {
                //    command.debug(trp.getName() + ": " + trp.getValueFloat());
                //}
            }
            for (int startbox = 0; startbox < startboxes.size(); startbox++) {

                for (Area a : getAreasInRectangle(new AIFloat3(startboxes.get(startbox).get(0) * mwidth, 0, startboxes.get(startbox).get(1) * mheight),
                        new AIFloat3(startboxes.get(startbox).get(2) * mwidth, 0, startboxes.get(startbox).get(3) * mheight))) {
                    a.setOwner(Owner.enemy);
                }
            }
            for (Team t : clbk.getAllyTeams()) {
                int startbox = (int) Math.round(t.getRulesParamFloat("start_box_id", -1));
                if (startbox < 0) {
                    throw new RuntimeException("Failed getting startbox info");
                }

                for (Area a : getAreasInRectangle(new AIFloat3(startboxes.get(startbox).get(0) * mwidth, 0, startboxes.get(startbox).get(1) * mheight),
                        new AIFloat3(startboxes.get(startbox).get(2) * mwidth, 0, startboxes.get(startbox).get(3) * mheight))) {
                    a.setOwner(Owner.own);
                }
            }
            //add fake commanders to enemy startpos
            for (int startbox = 0; startbox < startboxes.size(); startbox++) {

                for (Area a : getAreasInRectangle(new AIFloat3(startboxes.get(startbox).get(0) * mwidth, 0, startboxes.get(startbox).get(1) * mheight),
                        new AIFloat3(startboxes.get(startbox).get(2) * mwidth, 0, startboxes.get(startbox).get(3) * mheight))) {
                    if (a.getOwner() == Owner.enemy) {
                        AIFloat3 mid = new AIFloat3(startboxes.get(startbox).get(0) * mwidth, 0, startboxes.get(startbox).get(1) * mheight);
                        mid.add(new AIFloat3(startboxes.get(startbox).get(2) * mwidth, 0, startboxes.get(startbox).get(3) * mheight));
                        mid.scale(0.5f);
                        compos = mid;
                        break;
                    }
                }
            }
        } catch (Exception ex) {
            command.debug("Exception while parsing startscript: ", ex);
            command.debug("Mirroring position");
            AIFloat3 pos = new AIFloat3(command.getCommanderHandlers().iterator().next().getCommander().getPos());
            pos.x = mwidth - pos.x;
            pos.z = mheight - pos.z;
            compos = pos;
        }
        command.addFakeEnemy(new FakeCommander(compos, command, clbk));
        Mex closest = null;
        for (Mex m : mexes) {
            if (closest == null || closest.distanceTo(compos) > m.distanceTo(compos)) {
                closest = m;
            }
        }
        command.addFakeEnemy(new FakeMex(closest.pos, command, clbk));

    }

    public void setMexSpots(List<AIFloat3> spots) {
        for (AIFloat3 pos : spots) {
            Mex mex = new Mex(pos);
            mexes.add(mex);
            getArea(pos).addMex(mex);
        }
    }

    @Override
    public void unitFinished(AIUnit u) {
    }

    private boolean calculatedRandomWalks = false;

    private int areaCacheUpdateIndex = 0;

    @Override
    public void update(int frame) {
        if (frame == 1) {

            parseStartScript();
        }

        long time1 = System.currentTimeMillis();
        int areacounter = 0;
        while (System.currentTimeMillis() - time1 < 5) {
            for (int i = 0; i < 10; i++) {
                areacounter++;
                areas.get(areaCacheUpdateIndex).getZone();
                areas.get(areaCacheUpdateIndex++).getDanger();
                areaCacheUpdateIndex %= areas.size();
            }
        }
        if (frame % 60 == 32) {
            command.debug("Updated " + areacounter + " Area caches in " + (System.currentTimeMillis() - time1) + "ms.");
        }

        if (frame % 120 == 11 && pnl != null) {
            long time2 = System.currentTimeMillis();
            pnl.updateFramebuffer();
            pnl.updateUI();
            if (System.currentTimeMillis() - time2 > 10) {
                command.debug("Updating Framebuffer took " + (System.currentTimeMillis() - time2) + "ms.");
            }
        }
        /*if (!calculatedRandomWalks && frame > 5) {
         command.debug("Calculating flows..");
         calculatedRandomWalks = true;
         AIUnit own = null;
         for (AIUnit au : command.getUnits()) {
         if (own == null || au.getMetalCost() > own.getMetalCost()) {
         own = au;
         }
         }
         Enemy enemy = null;
         for (Enemy e : command.getEnemyUnits(true)) {
         if (enemy == null || e.getMetalCost() > enemy.getMetalCost()) {
         enemy = e;
         }
         }*/
        final int ITERATIONS = 10;
        final int PATH_LENGTH = 80;
        //MovementType movementType = MovementType.getMovementType(command.getFactoryHandler().getNextFac(new HashSet()).getBuildOptions().get(0));
        for (int i = 0; i < ITERATIONS; i++) {
            AIUnit own2 = command.getRandomUnit();
            int antilag = 10;
            /*while (!own2.isBuilding()) {
                own2 = command.getRandomUnit();
                if (antilag-- < 0) {
                    break;
                }
            }
            if (antilag < 0) {
                continue;
            }*/
            final AIUnit own = own2;
            final int units = command.getUnits().size();
            float value = own.getMetalCost();
            if (!own.isBuilding()) {
                value /= 3f;
            }
            if (own.getDef().getName().equalsIgnoreCase("cormex")) {
                value = 600;
            }
            final float finvalue = value;

            getArea(own.getPos()).randomWalk(PATH_LENGTH, new AreaChecker() {

                @Override
                public boolean checkArea(zkcbai.helpers.ZoneManager.Area a) {
                    a.addFlow(finvalue * units / (500 * ITERATIONS * PATH_LENGTH));
                    return true;
                    //return !own.isBuilding() || a.distanceTo(own.getPos()) < own.getMaxRange();
                }
            }, MovementType.spider /*own.isBuilding() ? MovementType.bot : own.getMovementType()*/);
            final Enemy enemy = command.getRandomEnemy();
            if (enemy != null) {
                final int enemies = command.getEnemyUnits(true).size();
                getArea(enemy.getPos()).randomWalk(PATH_LENGTH, new AreaChecker() {

                    @Override
                    public boolean checkArea(zkcbai.helpers.ZoneManager.Area a) {
                        a.addFlow(-enemy.getMetalCost() * enemies / (500 * ITERATIONS * PATH_LENGTH));
                        return true;
                        //return !enemy.isBuilding() || a.distanceTo(enemy.getPos()) < enemy.getMaxRange();
                    }
                }, enemy.isBuilding() ? MovementType.bot : enemy.getMovementType());
            }
        }
        //}
        if (frame % 17 == 0) {
            cmdsPerSec = cmds / 50f * 30;
            cmds = 0;
        }
        if (frame % 150 == 42) {
        }
        tasks = 0;
    }

    public Mex getNearestBuildableMex(AIFloat3 pos) {
        float minDist = Float.MAX_VALUE;
        Mex best = null;
        for (Mex m : mexes) {
            if (m.getOwner() == Owner.none && m.distanceTo(pos) < minDist) {
                minDist = m.distanceTo(pos);
                best = m;
            }
        }
        return best;
    }

    public Collection<Mex> getMexes() {
        return mexes;
    }

    public Mex getNearestMex(AIFloat3 pos) {
        float minDist = Float.MAX_VALUE;
        Mex best = null;
        for (Mex m : mexes) {
            if (m.distanceTo(pos) < minDist) {
                minDist = m.distanceTo(pos);
                best = m;
            }
        }
        return best;
    }

    public final List<Area> getAreas() {
        return areas;
    }

    public Area getArea(AIFloat3 pos) {
        return map[Math.max(0, Math.min(map.length - 1, map.length * (int) pos.x / mwidth))][Math.max(0, Math.min(map[0].length - 1, map[0].length * (int) pos.z / mheight))];
    }

    public List<Area> getAreasInRectangle(AIFloat3 coords, AIFloat3 coords2) {
        int x1 = (int) (coords.x * map.length / 8 / clbk.getMap().getWidth());
        int y1 = (int) (coords.z * map[0].length / 8 / clbk.getMap().getHeight());
        int x2 = (int) (coords2.x * map.length / 8 / clbk.getMap().getWidth());
        int y2 = (int) (coords2.z * map[0].length / 8 / clbk.getMap().getHeight());
        List<Area> ret = new ArrayList();
        for (int x = x1; x <= Math.min(x2, map.length - 1); x++) {
            for (int y = y1; y <= Math.min(y2, map[x].length - 1); y++) {
                ret.add(map[x][y]);
            }
        }
        return ret;
    }

    @Override
    public void unitDestroyed(AIUnit u, Enemy e) {

    }

    @Override
    public void unitDestroyed(Enemy e, AIUnit killer) {
        if (e.getDef().getName().equals("cormex")) {
            for (Mex m : mexes) {
                m.removeEnemyMex(e);
            }
        }
    }

    public AIUnit getNearestBuilding(AIFloat3 pos) {
        AIUnit best = null;
        for (AIUnit au : getArea(pos).getNearbyBuildings()) {
            if (best == null || best.distanceTo(pos) > au.distanceTo(pos)) {
                best = au;
            }
        }
        return best;
    }

    private int cmds = 0;
    private float cmdsPerSec;

    public void executedCommand() {
        cmds++;
    }

    private int tasks = 0;

    public void executedTask() {
        tasks++;
    }

    public int getExecutedTasks() {
        return tasks;
    }

    private float totalFlow = 0;
    private float flowDecay = 0.7f;

    public float getTotalFlow() {
        return totalFlow;
    }

    public class Area implements UpdateListener, UnitDestroyedListener, UnitFinishedListener {

        public final int x, y, index;
        private AIFloat3 pos;
        private Owner owner = Owner.none;
        private Zone zoneCache = Zone.neutral;
        private int lastCache = -111;
        private boolean reachable = false;
        private List<Area> neighbours;
        private Set<Mex> mexes = new HashSet();
        private List<Connection> connections = new ArrayList();
        private Map<MovementType, List<Connection>> connMap = new HashMap();
        private Set<DebugConnection> debugConns = new HashSet();
        private int lastPathCalcTime = -100000;
        private float flow = 0;
        private float negativeFlow = 0;
        private float positiveFlow = 0;

        public Area(int x, int y, int index) {
            this.x = x;
            this.y = y;
            this.index = index;
            this.pos = new AIFloat3((x + 0.5f) * mwidth / map.length, 0, (y + 0.5f) * mheight / map[0].length);
            this.pos.y = clbk.getMap().getElevationAt(pos.x, pos.z);
            command.addSingleUpdateListener(this, command.getCurrentFrame() + (int) (100 * Math.random()));
            command.addUnitDestroyedListener(this);
            command.addUnitFinishedListener(this);
        }

        @Override
        public int hashCode() {
            return index;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final Area other = (Area) obj;
            if (this.index != other.index) {
                return false;
            }
            return true;
        }

        public Collection<Connection> getConnections() {
            return connections;
        }

        public void addFlow(float amt) {
            totalFlow += Math.abs(amt);
            flow += amt;
            negativeFlow += Math.max(0, -amt);
            positiveFlow += Math.max(0, amt);
        }

        public float getFlow() {
            return flow;
        }

        public float getNegativeFlow() {
            return negativeFlow;
        }

        public float getPositiveFlow() {
            return positiveFlow;
        }

        public Collection<Connection> getConnections(MovementType mt) {
            if (connMap.get(mt) == null) {
                connMap.put(mt, new ArrayList());
            }
            return connMap.get(mt);
        }

        public int getLastPathCalculationTime() {
            return lastPathCalcTime;
        }

        boolean fancyCleared = false;

        public synchronized void clearFancyPaths() {
            if (fancyCleared) {
                return;
            }
            fancyCleared = true;
            List<Connection> fancy = new ArrayList();
            for (Connection c : getConnections()) {
                if (c.length < 0) {
                    fancy.add(c);
                }
            }
            connections.removeAll(fancy);
            for (Collection<Connection> clist : connMap.values()) {
                fancy = new ArrayList();
                for (Connection c : clist) {
                    if (c.length < 0) {
                        fancy.add(c);
                    }
                }
                clist.removeAll(fancy);
            }
        }

        public void recalculatePaths() {
            lastPathCalcTime = command.getCurrentFrame();

            for (int xx = Math.max(x - 1, 0); xx <= Math.min(x + 1, map.length - 1); xx++) {
                for (int yy = Math.max(y - 1, 0); yy <= Math.min(y + 1, map[0].length - 1); yy++) {
                    if (xx == x && yy == y || command.getCurrentFrame() - map[xx][yy].getLastPathCalculationTime() < 1000) {
                        continue;
                    }
                    for (Pathfinder.MovementType mt : Pathfinder.MovementType.values()) {
                        PathPrecalculatorThread.addPrecalcTask(this, mt, map[xx][yy]);
                    }
                }
            }
        }

        /**
         * add connection to one of its neighbouring areas
         *
         * @param target
         * @param length
         * @param mt
         */
        public synchronized void addConnection(Area target, float length, Pathfinder.MovementType mt) {
            connections.add(new Connection(target, length, mt));
            if (!connMap.containsKey(mt)) {
                connMap.put(mt, new ArrayList());
            }
            connMap.get(mt).add(connections.get(connections.size() - 1));
        }

        protected Queue<Connection> connQueue = new ConcurrentLinkedQueue<>();

        private int queued = 0;

        public void queueConnection(Area target, float length, Pathfinder.MovementType mt) {
            if (length >= 0) {
                connQueue.add(new Connection(target, length, mt));
            }
            queued++;
            int req = 32;
            if (queued >= req) {
                applyConnections();
            }
        }

        private int cnt = 0;

        public synchronized void applyConnections() {
            cnt++;
            if (cnt > 2) {
                command.debug("Warning: called apply connections multiple times, probable performance issue");
            }
            clearFancyPaths();
            while (!connQueue.isEmpty()) {
                addConnection(connQueue.peek().endpoint, connQueue.peek().length, connQueue.poll().movementType);
            }
        }

        public synchronized void clearConnections() {
            connections.clear();
            connMap.clear();
        }

        private void addMex(Mex m) {
            mexes.add(m);
        }

        public Collection<Mex> getMexes() {
            return mexes;
        }

        public boolean equals(Area a) {
            return x == a.x && y == a.y;
        }

        private void setOwner(Owner o) {
            owner = o;
        }

        public float distanceTo(AIFloat3 trg) {
            AIFloat3 pos = new AIFloat3(getPos());
            pos.sub(trg);
            pos.y = 0;
            return pos.length();
        }

        public void randomWalk(int depth, AreaChecker a, MovementType mt) {
            if (depth < 0) {
                return;
            }
            a.checkArea(this);
            List<Connection> conns = new ArrayList(getConnections(mt));
            if (conns.isEmpty()) {
                return;
            }
            conns.get(random.nextInt(conns.size())).endpoint.randomWalk(depth - 1, a, mt);
        }

        public Area getHighestArea(UnitDef building, float maxDistance) {
            int rw = (int) Math.floor(map.length * maxDistance / mwidth);
            int rh = (int) Math.floor(map[0].length * maxDistance / mheight);
            float h, maxh = 0;
            Area best = null;

            for (int x = Math.max(0, this.x - rw); x <= Math.min(map.length - 1, this.x + rw); x++) {
                for (int y = Math.max(0, this.y - rh); y <= Math.min(map[0].length - 1, this.y + rh); y++) {
                    h = clbk.getMap().getElevationAt(map[x][y].getPos().x, map[x][y].getPos().z);
                    if (h > maxh && command.isPossibleToBuildAt(building, map[x][y].getPos(), 0)) {
                        maxh = h;
                        best = map[x][y];
                    }
                }
            }
            return best;
        }

        List<Mex> nearbyMexes;

        public Collection<Mex> getNearbyMexes() {
            if (nearbyMexes == null) {
                nearbyMexes = new ArrayList();
                for (Mex m : ZoneManager.this.getMexes()) {
                    if (m.distanceTo(pos) < getEnclosingRadius() * 7) {
                        nearbyMexes.add(m);
                    }
                }
                //command.mark(pos, nearbyMexes.size() + "/" + getMexes().size() +  " nearby mexes");
            }
            return nearbyMexes;
        }

        public Set<Area> getConnectedAreas(MovementType mt, AreaChecker checker, Set<Area> areas) {
            if (areas.contains(this) || !checker.checkArea(this)) {
                return areas;
            }
            areas.add(this);
            for (Connection c : getConnections(mt)) {
                c.endpoint.getConnectedAreas(mt, checker, areas);

            }
            return areas;
        }

        public Set<Area> getConnectedAreas(MovementType mt) {
            return getConnectedAreas(mt, new AreaChecker() {

                @Override
                public boolean checkArea(Area a) {
                    return true;
                }
            });
        }

        public Set<Area> getConnectedAreas(MovementType mt, AreaChecker checker) {
            return getConnectedAreas(mt, checker, new HashSet());
        }

        public Area getNearestArea(AreaChecker checker) {
            return getNearestArea(checker, MovementType.air);
        }

        public Area getNearestArea(AreaChecker checker, MovementType md) {
            return getNearestArea(checker, md, true);
        }

        private final Queue<Area> _gna_pq = new LinkedList();

        private final Set<Area> _gna_visited = new HashSet<>();

        public Area getNearestArea(AreaChecker checker, MovementType md, boolean notNull) {
            Queue<Area> pq = _gna_pq;
            pq.clear();
            pq.add(this);
            Set<Area> visited = _gna_visited;
            visited.clear();
            visited.add(this);
            while (!pq.isEmpty()) {
                Area pos = pq.poll();
                if (checker.checkArea(pos)) {
                    return pos;
                }
                for (Connection c : pos.getConnections(md)) {
                    if (!visited.contains(c.endpoint)) {
                        visited.add(c.endpoint);
                        pq.add(c.endpoint);
                    }
                }
            }
            if (notNull) {
                return map[(int) (Math.random() * map.length)][(int) (Math.random() * map[0].length)];
            }
            return null;
        }

        /**
         *
         * @return whether area is in radar or los
         */
        public boolean isVisible() {
            return command.radarManager.isInRadar(pos) || command.losManager.isInLos(pos);
        }

        public boolean isInLOS() {
            return command.losManager.isInLos(pos);
        }

        public boolean isReachable() {
            return reachable;
        }

        public void setReachable() {
            setReachable(true);
        }

        public void setReachable(boolean val) {
            reachable = val;
        }

        List<Enemy> enemies;

        public final List<Enemy> getEnemies() {
            getDanger();
            return enemies;
        }

        List<Enemy> nearbyEnemies;
        Enemy[] nearbyEnemiesArr;

        public final Enemy[] getNearbyEnemies() {
            getDanger();
            return nearbyEnemiesArr;
        }

        private int lastDefDPSCache = -1000;
        private float DefDPSCache = 0;

        public float getDefenseDPS() {
            if (command.getCurrentFrame() - lastDefDPSCache > -1) {
                DefDPSCache = command.getBuilderHandler()._getDefenseDPS(pos);
                lastDefDPSCache = command.getCurrentFrame();
            }
            return DefDPSCache;
        }

        /**
         * forces cache update. cpu heavy!
         */
        public void updateEnemies() {
            lastDangerCache = -100;
            getDanger();
        }

        int lastDangerCache = -100;
        float dangerCache = 0;

        public float getNearbyRadius() {
            return 8 * getEnclosingRadius();
        }

        public float getDanger() {
            if (command.getCurrentFrame() - lastDangerCache > 20) {
                enemies = new ArrayList();
                nearbyEnemies = new ArrayList();
                lastDangerCache = command.getCurrentFrame();
                dangerCache = 0;
                for (Enemy e : command.getEnemyUnits(false)) {
                    float speed = 3 * Math.max(30, e.getDef().getSpeed());
                    dangerCache += e.getDPS() * e.getHealth() / speed / Math.sqrt(2 * Math.PI)
                            * Math.exp(-e.distanceTo(pos) * e.distanceTo(pos) / (speed * speed));
                    if (getArea(e.getPos()).equals(this)) {
                        enemies.add(e);
                    }
                    if (e.distanceTo(pos) < getNearbyRadius()) {
                        nearbyEnemies.add(e);
                    }
                }
                nearbyEnemiesArr = nearbyEnemies.toArray(new Enemy[0]);
            }
            return dangerCache;
        }

        int lastValueCache = -100;
        float valueCache = 0;
        int lastEnemyValueCache = -100;
        float enemyValueCache = 0;

        private int lastIsFrontCache = -200;
        private boolean isFrontCache;

        public boolean isFront() {
            if (getZone() != Zone.own) {
                return false;
            }
            if (command.getCurrentFrame() - lastIsFrontCache > 100) {
                int hostile = 0;
                int neutral = 0;
                int own = 0;
                int radius = 3;
                for (int dx = -radius; dx <= radius; dx++) {
                    for (int dy = -radius; dy <= radius; dy++) {
                        if (x + dx >= map.length || y + dy >= map[0].length || x + dx < 0 || y + dy < 0) {
                            continue;
                        }
                        Zone zone = map[x + dx][y + dy].getZone();
                        if (zone == Zone.hostile || zone == Zone.fortified) {
                            hostile++;
                        } else if (zone == Zone.neutral) {
                            neutral++;
                        } else if (zone == Zone.own) {
                            own++;
                        }
                    }
                }
                lastIsFrontCache = command.getCurrentFrame();
                isFrontCache = (neutral + hostile) / (float) (neutral + hostile + own) > 0.2;
            }
            return isFrontCache;
        }

        public float getValue() {
            if (command.getCurrentFrame() - lastValueCache > 60) {
                lastValueCache = command.getCurrentFrame();
                valueCache = 0;
                for (AIUnit u : command.getUnits()) {
                    float speed = 8 * Math.max(40, u.getUnit().getDef().getSpeed());
                    float val = u.getUnit().getDef().getCost(command.metal);
                    float dist = Math.max(0, u.distanceTo(pos));
                    if (u.getUnit().getResourceMake(command.metal) > 0 && !u.getDef().getHumanName().contains("com")) {
                        val *= 10;
                    }
                    valueCache += val / speed / Math.sqrt(2 * Math.PI)
                            * Math.exp(-dist * dist / (speed * speed));
                }
            }
            return valueCache;
        }

        public float getEnemyValue() {
            if (command.getCurrentFrame() - lastEnemyValueCache > 60) {
                lastEnemyValueCache = command.getCurrentFrame();
                enemyValueCache = 0;
                for (Enemy e : command.getEnemyUnits(true)) {
                    float speed = 8 * Math.max(40, e.getDef().getSpeed());
                    float val = e.getDef().getCost(command.metal);
                    float dist = Math.max(0, e.distanceTo(pos));
                    if (e.getDef().getResourceMake(command.metal) > 0 && !e.getDef().getHumanName().contains("com")) {
                        val *= 10;
                    }
                    enemyValueCache += val / speed / Math.sqrt(2 * Math.PI)
                            * Math.exp(-dist * dist / (speed * speed));
                }
            }
            return enemyValueCache;
        }

        public AIFloat3 getPos() {
            return new AIFloat3(pos);
        }

        public Zone getZone() {
            if (command.getCurrentFrame() - lastCache > 60) {
                Zone old = zoneCache;
                zoneCache = _getZone();
                if (old != zoneCache) {
                    for (AreaZoneChangeListener listener : zoneChangeListeners) {
                        listener.areaZoneChange(this, old, zoneCache);
                    }
                }
                lastCache = command.getCurrentFrame();
            }
            return zoneCache;
        }

        private Owner getOwner() {
            return owner;
        }

        private Zone _getZone() {
            /*
             if (isVisible() && owner == Owner.enemy && getEnemies().isEmpty()) {
             owner = Owner.none;
             }

             if (command.defenseManager.getDanger(pos, Math.max(mwidth / map.length, mheight / map[0].length) / 2f + 50) > 0 || getDanger() > 100) {
             owner = Owner.enemy;
             return Zone.hostile;
             }
             if (getEnemyValue() > 0.3) {
             owner = Owner.none;
             return Zone.neutral;
             }
             if (getValue() > 0.3) {
             owner = Owner.own;
             return Zone.own;
             }
             if (owner == Owner.own) {
             return Zone.own;
             }
             if (owner == Owner.enemy) {
             //    return Zone.hostile;
             }*/
            boolean inEnemyTurretRange = false;
            for (Enemy e : getNearbyEnemies()) {
                if (e.isBuilding() && e.distanceTo(pos) < e.getMaxRange()) {
                    inEnemyTurretRange = true;
                }
            }
            if (getFlow() > 5e-1 && !inEnemyTurretRange) {
                return Zone.own;
            }
            if (getFlow() < -5e-1) {
                return Zone.hostile;
            }
            return Zone.neutral;
        }

        public List<Area> getNeighbours() {
            if (neighbours == null) {
                neighbours = new ArrayList();
                for (int x = this.x - 1; x <= this.x + 1; x++) {
                    for (int y = this.y - 1; y <= this.y + 1; y++) {
                        if (x >= 0 && y >= 0 && x < map.length && y < map[0].length) {
                            neighbours.add(map[x][y]);
                        }
                    }
                }
            }
            return neighbours;
        }

        public boolean isSafe() {

            boolean inEnemyTurretRange = false;
            for (Enemy e : getNearbyEnemies()) {
                if (e.distanceTo(pos) < 1.5 * e.getMaxRange()) {
                    inEnemyTurretRange = true;
                }
            }
            return !inEnemyTurretRange;
        }

        /**
         *
         * @return width of the area
         */
        public float getWidth() {
            return (float) mwidth / map.length;
        }

        /**
         *
         * @return height of the area
         */
        public float getHeight() {
            return (float) mheight / map[0].length;
        }

        /**
         *
         * @return radius of circle enclosing the area
         */
        public float getEnclosingRadius() {
            return Math.max(getWidth(), getHeight());
        }

        protected Set<AIUnit> nearbyBuildings = new HashSet();

        public Collection<AIUnit> getNearbyBuildings() {
            return nearbyBuildings;
        }

        public Collection<AIUnit> getNearbyUnits(float range) {
            List<Unit> units = clbk.getFriendlyUnitsIn(pos, range);
            List<AIUnit> aiunits = new ArrayList();
            for (Unit u : units) {
                if (command.getAIUnit(u) != null) {
                    aiunits.add(command.getAIUnit(u));
                }
            }
            return aiunits;
        }

        public void addDebugConnection(Area target, Color color, int timeout) {
            debugConns.add(new DebugConnection(target, color, timeout));
        }

        public Collection<DebugConnection> getDebugConnections() {
            return debugConns;
        }

        @Override
        public void update(int frame) {
            getZone();
            totalFlow -= Math.abs(flow * (1 - flowDecay));
            flow *= flowDecay;
            negativeFlow *= flowDecay;
            positiveFlow *= flowDecay;
            command.addSingleUpdateListener(this, command.getCurrentFrame() + 100);
        }

        @Override
        public void unitDestroyed(AIUnit u, Enemy killer) {
            nearbyBuildings.remove(u);
        }

        @Override
        public void unitDestroyed(Enemy e, AIUnit killer) {
            enemies.remove(e);
            //command.debug("enemy destroyed " + e.getUnitId());
            if (nearbyEnemies.remove(e)) {
                //command.debug("removing from list " + e.getUnitId());
                nearbyEnemiesArr = nearbyEnemies.toArray(new Enemy[0]);
            }
        }

        @Override
        public void unitFinished(AIUnit u) {
            if (u.getDef().getSpeed() < 0.01 && u.distanceTo(pos) < getEnclosingRadius() * 3) {
                nearbyBuildings.add(u);
            }
        }

        public class Connection {

            public final Area endpoint;
            public final float length;
            public final Pathfinder.MovementType movementType;

            public Connection(Area endpoint, float length, Pathfinder.MovementType movementType) {
                this.endpoint = endpoint;
                this.length = length;
                this.movementType = movementType;
            }
        }

        public class DebugConnection implements UpdateListener {

            public final Area endpoint;
            public final Color color;
            public final int timeout;

            public DebugConnection(Area endpoint, Color color, int timeout) {
                this.endpoint = endpoint;
                this.color = color;
                this.timeout = timeout;
                command.addSingleUpdateListener(this, timeout);
            }

            @Override
            public void update(int frame) {
                debugConns.remove(this);
            }
        }
    }

    /**
     *
     * @return width of the area array
     */
    public int getWidth() {
        return map.length;
    }

    /**
     *
     * @return height of the area array
     */
    public int getHeight() {
        return map[0].length;
    }

    public class Mex {

        public final AIFloat3 pos;

        private Enemy enemy;

        private BuildTask buildTask;

        public float getIncome() {
            return 1.5f;
        }

        public Mex(AIFloat3 pos) {
            this.pos = pos;
        }

        public void setEnemyMex(Enemy e) {
            this.enemy = e;
        }

        public void removeEnemyMex(Enemy e) {
            if (this.enemy != null && this.enemy.equals(e)) {
                this.enemy = null;
            }
        }

        public Enemy getEnemy() {
            return enemy;
        }

        public boolean isBuilt() {
            //command.debug("checking whether mex has been built@" + pos.toString());
            /*if (command.getCallback().getMap().isPossibleToBuildAt(clbk.getUnitDefByName("cormex"), pos, 0) == false) {
             command.mark(pos, "mex is built");
             }*/
 /*try { //hardcore debugging
             throw new RuntimeException();
             } catch (Exception ex) {
             command.debug("at ", ex);
             }*/
            return !command.getCallback().getMap().isPossibleToBuildAt(clbk.getUnitDefByName("cormex"), pos, 0);
        }

        public Owner getOwner() {
            if (enemy != null) {
                return Owner.enemy;
            }
            if (!command.isPossibleToBuildAt(clbk.getUnitDefByName("cormex"), pos, 0)
                    || (buildTask != null && buildTask.isBeingWorkedOn(command.getCurrentFrame() - 600))) {
                return Owner.own;
            }
            return Owner.none;
        }

        public float distanceTo(AIFloat3 trg) {
            AIFloat3 pos = new AIFloat3(this.pos);
            pos.sub(trg);
            return pos.length();
        }

        public BuildTask getBuildTask() {
            if (buildTask != null && ((buildTask.isDone() || !buildTask.isBeingWorkedOn(command.getCurrentFrame() - 30 * 60 * 2)) && !isBuilt())) {
                buildTask = null;
            }
            return buildTask;
        }

        public BuildTask createBuildTask(TaskIssuer t) {
            if (getBuildTask() == null && command.isPossibleToBuildAt(clbk.getUnitDefByName("cormex"), pos, 0)) {
                buildTask = new BuildTask(clbk.getUnitDefByName("cormex"), pos, t, clbk, command, 0);
                buildTask.setInfo("mex");
            }
            if (buildTask == null) {
                for (Unit u : clbk.getFriendlyUnitsIn(pos, 50)) {
                    u.selfDestruct((short) 0, Integer.MAX_VALUE);
                }
            }
            return buildTask;
        }

    }

    class MapGUI extends JPanel {

        public MapGUI() {
            super();
            command.debug("MapGUI initialized");

        }

        BufferedImage framebuffer = new BufferedImage(1, 1, BufferedImage.TYPE_3BYTE_BGR);

        @Override
        protected void paintComponent(Graphics g) {

            g.drawImage(framebuffer, 0, 0, null);

        }

        public void updateFramebuffer() {
            long time0 = System.currentTimeMillis();
            BufferedImage newimg = new BufferedImage(getWidth(), getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics g = newimg.getGraphics();
            float[][] val = new float[getWidth()][getHeight()];

            long time1 = System.currentTimeMillis();
            float w = getWidth() / (float) map.length;
            float h = getHeight() / (float) map[0].length;

            float maxDanger = Float.MIN_VALUE;
            float maxValue = Float.MIN_VALUE;
            float maxImportance = Float.MIN_VALUE;
            for (int x = 0; x < map.length; x++) {
                for (int y = 0; y < map[x].length; y++) {
                    maxDanger = Math.max(maxDanger, map[x][y].getDanger());
                    maxValue = Math.max(maxValue, map[x][y].getValue());
                    maxImportance = Math.max(maxImportance, map[x][y].getValue() * map[x][y].getDanger());
                }
            }
            long time2 = System.currentTimeMillis();
            for (int x = 0; x < map.length; x++) {
                for (int y = 0; y < map[x].length; y++) {
                    Color stringcol = Color.black;
                    switch (map[x][y].getZone()) {
                        case own:
                            g.setColor(Color.blue);
                            //g.setColor(new Color(Area.map[x][y].guaranteedUnits*255/Area.totUnits,Area.map[x][y].guaranteedUnits*255/Area.totUnits , 255-Area.map[x][y].guaranteedUnits*255/Area.totUnits));
                            stringcol = Color.white;
                            break;
                        case hostile:
                            g.setColor(new Color(200, 0, 0));
                            stringcol = Color.white;
                            break;
                        case fortified:
                            g.setColor(new Color(100, 0, 0));
                            stringcol = Color.white;
                            break;
                        case neutral:
                            g.setColor(new Color(200, 200, 200));
                            stringcol = Color.white;
                            break;
                    }
                    if (map[x][y].isFront()) {
                        g.setColor(new Color(190, 0, 190));
                        stringcol = Color.white;
                    }
                    /*
                     if (ScoutSquad.dangerChecker.checkArea(map[x][y])) {
                     g.setColor(Color.green);
                     } else {
                     g.setColor(Color.red);
                     }*/

                    float minFlow = -0.3f;
                    float maxFlow = 0.3f;
                    float flow = Math.signum(map[x][y].getFlow()) * (float) Math.pow(Math.abs(map[x][y].getFlow()), 0.4f);
                    //g.setColor(interpolate(Color.green, Color.red, Math.min(2 * maxFlow, Math.max(0, flow - minFlow)) / (2 * maxFlow)));

                    if (!command.losManager.isInLos(map[x][y].getPos())) {
                        g.setColor(g.getColor().darker());
                        if (!command.radarManager.isInRadar(map[x][y].getPos())) {
                            g.setColor(g.getColor().darker());
                        }
                    }
                    float danger = map[x][y].getDanger() / maxDanger;
                    float value = map[x][y].getValue() / maxValue;
                    /*float[] hsb = new float[3];
                     Color.RGBtoHSB(g.getColor().getRed(), g.getColor().getGreen(), g.getColor().getBlue(),hsb );
                     if (map[x][y].getZone() != Zone.own){
                     hsb[0] = 0;
                     hsb[1] = map[x][y].getDanger()*map[x][y].getValue()/maxImportance;
                     g.setColor(new Color(Color.HSBtoRGB(hsb[0], hsb[1], hsb[2])));
                     }*/
                    g.fillRect((int) Math.round(x * w), (int) Math.round(y * h), (int) w + 1, (int) h + 1);
                    //g.setColor(Color.darkGray);
                    int dx[] = {-1, 0};
                    int dy[] = {0, -1};
                    for (int blub = 0; blub < dx.length; blub++) {
                        if (x + dx[blub] < 0) {
                            continue;
                        }
                        if (y + dy[blub] < 0) {
                            continue;
                        }
                        float minSlope = 2;
                        for (Connection c : map[x][y].getConnections()) {
                            if (c.endpoint.equals(map[x + dx[blub]][y + dy[blub]])) {
                                minSlope = Math.min(minSlope, c.movementType.getMaxSlope());
                            }
                        }
                        g.setColor(Color.white);
                        switch (MovementType.getMovementType(minSlope)) {
                            case air:
                                g.setColor(Color.black.darker());
                                break;
                            case bot:
                                g.setColor(Color.yellow.darker());
                                break;
                            case spider:
                                g.setColor(Color.red.darker());
                                break;
                            case vehicle:
                                g.setColor(Color.green.darker());
                                break;
                        }
                        g.drawLine((int) Math.round(x * w), (int) Math.round(y * h),
                                (int) Math.round((x - dy[blub]) * w), (int) Math.round((y - dx[blub]) * h));
                    }
                    if (!map[x][y].isReachable()) {
                        g.setColor(Color.red.darker());
                        g.drawLine((int) Math.round(x * w), (int) Math.round(y * h + h),
                                (int) Math.round((x + 1) * w), (int) Math.round((y) * h));
                    }
                    for (DebugConnection dc : map[x][y].getDebugConnections()) {

                        g.setColor(dc.color);
                        g.drawLine((int) Math.round((x + 0.5f) * w), (int) Math.round((y + 0.5) * h),
                                (int) Math.round((dc.endpoint.x + 0.5) * w), (int) Math.round((dc.endpoint.y + 0.5) * h));
                    }
                    //g.drawRect((int)Math.round(x * w), (int)Math.round(y * h), (int)Math.round(w), (int)Math.round(h));
                    g.setColor(stringcol);
                    //g.drawString(String.valueOf(Area.map[x][y].guaranteedUnits), x * w + w / 2 - 2, y * h + h / 2 + 4);
                }
            }

            long time3 = System.currentTimeMillis();
            for (Mex m : mexes) {
                switch (m.getOwner()) {
                    case own:
                        g.setColor(Color.green);
                        break;
                    case none:
                        g.setColor(Color.black);
                        break;
                    case enemy:
                        g.setColor(Color.orange);
                        break;
                }
                int x = (int) Math.round(m.pos.x * getWidth() / mwidth);
                int y = (int) Math.round(m.pos.z * getHeight() / mheight);
                g.drawOval(x - 5, y - 5, 10, 10);
                g.drawOval(x - 4, y - 4, 8, 8);
            }

            long time4 = System.currentTimeMillis();
            g.setColor(Color.orange);
            g.drawString(Math.round(cmdsPerSec * 10) / 10f + " cmds/sec", 10, 10);

            g.drawString("Offense budget: " + Math.round(command.economyManager.getRemainingOffenseBudget()), 10, 25);
            g.drawString("Energy budget: " + Math.round(command.economyManager.getRemainingEnergyBudget()), 10, 40);
            g.drawString("Defense budget: " + Math.round(command.economyManager.getRemainingDefenseBudget()), 10, 55);
            g.drawString("Grid nodes: " + command.getBuilderHandler().getGridNodes().size(), 10, 70);
            String queue = "";
            String building = "";
            for (Factory f : command.getFactoryHandler().getFacs()) {
                for (UnitDef ud : f.getBuildQueue()) {
                    queue += ud.getHumanName() + ", ";
                }
                if (f.getCurrentTask() != null) {
                    building += f.getCurrentTask().getBuilding().getHumanName() + ", ";
                }
            }
            g.drawString("Queue: " + queue, 10, 85);
            g.drawString("Building: " + building, 10, 100);
            g.setColor(Color.yellow);
            for (GridNode gn : command.getBuilderHandler().getGridNodes()) {
                int x = (int) Math.round((gn.pos.x - gn.range) * getWidth() / mwidth);
                int y = (int) Math.round((gn.pos.z - gn.range) * getHeight() / mheight);
                int cw = (int) Math.round(2 * gn.range * getWidth() / mwidth);
                int ch = (int) Math.round(2 * gn.range * getHeight() / mheight);
                g.drawOval(x, y, cw, ch);
            }
            g.setColor(Color.red);
            for (GridNode gn : command.getBuilderHandler().getDefenseNodes()) {
                int x = (int) Math.round((gn.pos.x - gn.range) * getWidth() / mwidth);
                int y = (int) Math.round((gn.pos.z - gn.range) * getHeight() / mheight);
                int cw = (int) Math.round(2 * gn.range * getWidth() / mwidth);
                int ch = (int) Math.round(2 * gn.range * getHeight() / mheight);
                g.drawOval(x, y, cw, ch);
            }

            long time5 = System.currentTimeMillis();
            for (Enemy e : command.getEnemyUnitsIn_Slow(new AIFloat3(), 10000)) {
                String name = e.getDef().getHumanName() + " " + Math.ceil(e.getRelativeHealth() * 100) + "%";
                Graphics2D g2d = (Graphics2D) g;
                FontMetrics fm = g2d.getFontMetrics();
                Rectangle2D r = fm.getStringBounds(name, g2d);
                int x = (int) Math.round(e.getPos().x * getWidth() / mwidth) - (int) r.getWidth() / 2;
                int y = (int) Math.round(e.getPos().z * getHeight() / mheight) - (int) r.getHeight() / 2 + fm.getAscent();

                g.setColor(Color.black);
                g.fillRect(x, y - fm.getAscent(), (int) Math.round(r.getWidth()), (int) Math.round(r.getHeight()));

                g.setColor(Color.orange);
                g.drawString(name, x, y);
            }

            long time6 = System.currentTimeMillis();
            framebuffer = newimg;
            time6 -= time5;
            time5 -= time4;
            time4 -= time3;
            time3 -= time2;
            time2 -= time1;
            time1 -= time0;
            //command.debug(time6 + " " + time5 + " " + time4 + " " + time3 + " " + time2 + " " + time1);
        }

        Color interpolate(Color a, Color b, float fac) {
            Color col = new Color(Math.round(a.getRed() * (fac) + b.getRed() * (1 - fac)),
                    Math.round(a.getGreen() * (fac) + b.getGreen() * (1 - fac)),
                    Math.round(a.getBlue() * (fac) + b.getBlue() * (1 - fac)));
            float max = 255f / Math.max(Math.max(col.getRed(), col.getGreen()), col.getBlue());
            return new Color(Math.round(col.getRed() * max), Math.round(col.getGreen() * max), Math.round(col.getBlue() * max));
        }

    }

}

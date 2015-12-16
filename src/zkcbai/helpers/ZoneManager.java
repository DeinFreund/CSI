/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package zkcbai.helpers;

import com.springrts.ai.oo.AIFloat3;
import com.springrts.ai.oo.clb.GameRulesParam;
import com.springrts.ai.oo.clb.OOAICallback;
import com.springrts.ai.oo.clb.Team;
import com.springrts.ai.oo.clb.TeamRulesParam;
import com.springrts.ai.oo.clb.UnitDef;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.swing.JFrame;
import javax.swing.JPanel;
import zkcbai.Command;
import zkcbai.UnitDestroyedListener;
import zkcbai.UnitFinishedListener;
import zkcbai.UpdateListener;
import static zkcbai.helpers.Helper.command;
import zkcbai.helpers.Pathfinder.MovementType;
import zkcbai.helpers.ZoneManager.Area.Connection;
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

    private Area[][] map;
    private List<Area> areas;//for simplified iterating

    private JFrame frm;
    private JPanel pnl;

    private List<Mex> mexes;

    private final int mwidth;
    private final int mheight;

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
        for (int x = 0; x < map.length; x++) {
            for (int y = 0; y < map[x].length; y++) {
                map[x][y] = new Area(x, y, arindex++);
                areas.add(map[x][y]);
            }
        }
        mexes = new ArrayList();
        Collections.shuffle(areas);
        Collections.shuffle(mexes);
        cmd.addUnitDestroyedListener(this);

        frm = new JFrame("MapGUI");
        pnl = new MapGUI();
        frm.add(pnl);
        frm.setVisible(true);
        frm.setSize(500, 500 * clbk.getMap().getHeight() / clbk.getMap().getWidth());
    }

    @Override
    public void init() {

        command.debug("Zone Manager precalculating paths...");
        int i = 0;
        for (Area a : areas) {
            a.recalculatePaths();
            if (i++ % 17 == 0) {
                pnl.updateUI();
            }
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
            for (GameRulesParam grp : clbk.getGame().getGameRulesParams()) {
                //  command.debug(grp.getName() + ": " + grp.getValueFloat());
            }
            command.debug("startscript:\n" + script);
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
                for (TeamRulesParam trp : t.getTeamRulesParams()) {
                    command.debug(trp.getName() + ": " + trp.getValueFloat());
                }
            }
            for (int startbox = 0; startbox < startboxes.size(); startbox++) {

                for (Area a : getAreasInRectangle(new AIFloat3(startboxes.get(startbox).get(0) * mwidth, 0, startboxes.get(startbox).get(1) * mheight),
                        new AIFloat3(startboxes.get(startbox).get(2) * mwidth, 0, startboxes.get(startbox).get(3) * mheight))) {
                    a.setOwner(Owner.enemy);
                }
            }
            for (Team t : clbk.getAllyTeams()) {
                int startbox = (int) Math.round(t.getTeamRulesParamByName("start_box_id").getValueFloat());

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

    @Override
    public void update(int frame) {
        if (frame == 1) {

            parseStartScript();
        }
        if (frame % 50 == 0) {
            cmdsPerSec = cmds / 50f * 30;
            pnl.updateUI();
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
        private int lastPathCalcTime = -100000;

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

        public Collection<Connection> getConnections() {
            return connections;
        }

        public int getLastPathCalculationTime() {
            return lastPathCalcTime;
        }

        public void recalculatePaths() {
            lastPathCalcTime = command.getCurrentFrame();
            for (int xx = Math.max(x - 1, 0); xx <= Math.min(x + 1, map.length - 1); xx++) {
                for (int yy = Math.max(y - 1, 0); yy <= Math.min(y + 1, map[0].length - 1); yy++) {
                    if (xx == x && yy == y || command.getCurrentFrame() - map[xx][yy].getLastPathCalculationTime() < 1000) {
                        continue;
                    }
                    for (Pathfinder.MovementType mt : Pathfinder.MovementType.values()) {
                        command.pathfinder.precalcPath(this, mt, map[xx][yy]);
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
        public void addConnection(Area target, float length, Pathfinder.MovementType mt) {
            connections.add(new Connection(target, length, mt));
        }

        public void clearConnections() {
            connections.clear();
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

        public Set<Area> getConnectedAreas(MovementType mt, Set<Area> areas) {
            if (areas.contains(this)) {
                return areas;
            }
            areas.add(this);
            for (Connection c : connections) {
                if (c.movementType.equals(mt)) {
                    c.endpoint.getConnectedAreas(mt, areas);
                }
            }
            return areas;
        }

        public Set<Area> getConnectedAreas(MovementType mt) {
            return getConnectedAreas(mt, new HashSet());
        }

        public Area getNearestArea(AreaChecker checker) {
            for (int radius = 0; radius < Math.max(map.length, map[0].length); radius++) {
                int x, y;
                x = this.x - radius;
                for (y = this.y - radius; y <= this.y + radius; y++) {
                    if (x >= 0 && y >= 0 && x < map.length && y < map[0].length && checker.checkArea(map[x][y])) {
                        return map[x][y];
                    }
                }
                x = this.x + radius;
                for (y = this.y - radius; y <= this.y + radius; y++) {
                    if (x >= 0 && y >= 0 && x < map.length && y < map[0].length && checker.checkArea(map[x][y])) {
                        return map[x][y];
                    }
                }
                y = this.y - radius;
                for (x = this.x - radius; x <= this.x + radius; x++) {
                    if (x >= 0 && y >= 0 && x < map.length && y < map[0].length && checker.checkArea(map[x][y])) {
                        return map[x][y];
                    }
                }
                y = this.y + radius;
                for (x = this.x - radius; x <= this.x + radius; x++) {
                    if (x >= 0 && y >= 0 && x < map.length && y < map[0].length && checker.checkArea(map[x][y])) {
                        return map[x][y];
                    }
                }
            }
            return null;
            //return map[(int) (Math.random() * map.length)][(int) (Math.random() * map[0].length)];
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
            return enemies;
        }

        List<Enemy> nearbyEnemies;

        public final List<Enemy> getNearbyEnemies() {
            getDanger();
            return nearbyEnemies;
        }

        int lastDangerCache = -100;
        float dangerCache = 0;

        public float getDanger() {
            if (command.getCurrentFrame() - lastDangerCache > 30) {
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
                    if (e.distanceTo(pos) < 750) {
                        nearbyEnemies.add(e);
                    }
                }
            }
            return dangerCache;
        }

        int lastValueCache = -100;
        float valueCache = 0;
        int lastEnemyValueCache = -100;
        float enemyValueCache = 0;

        public boolean isFront() {
            int hostile = 0;
            int neutral = 0;
            for (int dx = -1; dx < 2; dx++) {
                for (int dy = -1; dy < 2; dy++) {
                    if (x + dx >= map.length || y + dy >= map[0].length || x + dx < 0 || y + dy < 0) {
                        continue;
                    }
                    Zone zone = map[x + dx][y + dy].getZone();
                    if (zone == Zone.hostile || zone == Zone.fortified) {
                        hostile++;
                    } else {
                        neutral++;
                    }
                }
            }
            return hostile > 0 && neutral > 0;
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
            /*if (!isVisible() && owner == Owner.enemy) {
             return Zone.hostile;
             } else {
             owner = Owner.none;
             }*/
            if (isVisible() && owner == Owner.enemy && getEnemies().isEmpty()) {
                owner = Owner.none;
            }/*
             if (command.defenseManager.isFortified(pos, Math.max(mwidth / map.length, mheight / map[0].length) / 2f)) {
             owner = Owner.enemy;
             return Zone.fortified;
             }*/

            if (command.defenseManager.getDanger(pos, Math.max(mwidth / map.length, mheight / map[0].length) / 2f + 50) > 0 || getDanger() > 100) {
                owner = Owner.enemy;
                return Zone.hostile;
            }
            if (getEnemyValue() > 0.3) {
                owner = Owner.none;
                return Zone.neutral;
            }/*
             if (owner == Owner.none) {
             for (Area a : getNeighbours()) {
             if (a.getOwner() == Owner.own) {
             owner = Owner.own;
             break;
             }
             }
             }*/

            if (getValue() > 0.3) {
                owner = Owner.own;
                return Zone.own;
            }
            if (owner == Owner.own) {
                return Zone.own;
            }
            if (owner == Owner.enemy) {
                //    return Zone.hostile;
            }
            return Zone.own;
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

        @Override
        public void update(int frame) {
            getZone();
            command.addSingleUpdateListener(this, command.getCurrentFrame() + 100);
        }

        @Override
        public void unitDestroyed(AIUnit u, Enemy killer) {
            nearbyBuildings.remove(u);
        }

        @Override
        public void unitDestroyed(Enemy e, AIUnit killer) {
            enemies.remove(e);
            nearbyEnemies.remove(e);
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
            return buildTask;
        }

        public BuildTask createBuildTask(TaskIssuer t) {
            if (buildTask == null && command.isPossibleToBuildAt(clbk.getUnitDefByName("cormex"), pos, 0)) {
                buildTask = new BuildTask(clbk.getUnitDefByName("cormex"), pos, t, clbk, command, 0);
                buildTask.setInfo("mex");
            }
            return buildTask;
        }

    }

    class MapGUI extends JPanel {

        public MapGUI() {
            super();
            command.debug("MapGUI initialized");

        }

        @Override
        protected void paintComponent(Graphics g) {

            float[][] val = new float[getWidth()][getHeight()];

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
                    //g.drawRect((int)Math.round(x * w), (int)Math.round(y * h), (int)Math.round(w), (int)Math.round(h));
                    g.setColor(stringcol);
                    //g.drawString(String.valueOf(Area.map[x][y].guaranteedUnits), x * w + w / 2 - 2, y * h + h / 2 + 4);
                }
            }
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
            for (Enemy e : command.getEnemyUnitsIn(new AIFloat3(), 10000)) {
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

        }

    }

}

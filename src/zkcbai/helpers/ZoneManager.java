/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package zkcbai.helpers;

import com.springrts.ai.oo.AIFloat3;
import com.springrts.ai.oo.clb.OOAICallback;
import com.springrts.ai.oo.clb.UnitDef;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.JFrame;
import javax.swing.JPanel;
import zkcbai.Command;
import zkcbai.UnitDestroyedListener;
import zkcbai.unitHandlers.units.AITroop;
import zkcbai.unitHandlers.units.AIUnit;
import zkcbai.unitHandlers.units.Enemy;
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

        own, front, fortified, hostile, neutral
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

    public ZoneManager(Command cmd, OOAICallback clbk) {
        super(cmd, clbk);
        mwidth = clbk.getMap().getWidth() * 8;
        mheight = clbk.getMap().getHeight() * 8;
        map = new Area[50][50];
        areas = new ArrayList();
        for (int x = 0; x < map.length; x++) {
            for (int y = 0; y < map[x].length; y++) {
                map[x][y] = new Area(x, y);
                areas.add(map[x][y]);
            }
        }
        mexes = new ArrayList();
        cmd.addUnitDestroyedListener(this);
        parseStartScript();

        frm = new JFrame("MapGUI");
        pnl = new MapGUI();
        frm.add(pnl);
        frm.setVisible(true);
        frm.setSize(500, 500 * clbk.getMap().getHeight() / clbk.getMap().getWidth());
    }

    public void addAreaZoneChangeListener(AreaZoneChangeListener listener) {
        zoneChangeListeners.add(listener);
    }

    public void removeAreaZoneChangeListener(AreaZoneChangeListener listener) {
        zoneChangeListeners.remove(listener);
    }

    //code by Anarchid: https://github.com/Anarchid/zkgbai/blob/master/src/zkgbai/ZKGraphBasedAI.java
    private void parseStartScript() {
        String script = clbk.getGame().getSetupScript();
        Pattern p = Pattern.compile("\\[allyteam(\\d)\\]\\s*\\{([^\\}]*)\\}");
        Matcher m = p.matcher(script);
        AIFloat3 retval = new AIFloat3();
        while (m.find()) {
            int allyTeamId = Integer.parseInt(m.group(1));
            String teamDefBody = m.group(2);
            Pattern sbp = Pattern.compile("startrect\\w+=(\\d+(\\.\\d+)?);");
            Matcher mb = sbp.matcher(teamDefBody);

            float[] startbox = new float[4];
            int i = 0;

            // 0 -> bottom
            // 1 -> left
            // 2 -> right
            // 3 -> top
            while (mb.find()) {
                startbox[i] = Float.parseFloat(mb.group(1));
                i++;
            }

            int mapWidth = 8 * clbk.getMap().getWidth();
            int mapHeight = 8 * clbk.getMap().getHeight();

            startbox[0] *= mapHeight;
            startbox[1] *= mapWidth;
            startbox[2] *= mapWidth;
            startbox[3] *= mapHeight;

            for (Area a : getAreasInRectangle(new AIFloat3(startbox[1], 0, startbox[3]), new AIFloat3(startbox[2], 0, startbox[0]))) {
                a.setOwner((allyTeamId == clbk.getGame().getMyAllyTeam()) ? Owner.own : Owner.enemy);
            }
        }
    }

    public void setMexSpots(List<AIFloat3> spots) {
        for (AIFloat3 pos : spots) {
            mexes.add(new Mex(pos));
        }
    }

    @Override
    public void unitFinished(AIUnit u) {
    }

    @Override
    public void update(int frame) {
        if (frame % 50 == 0) {
            cmdsPerSec = cmds / 50f * 30;
            pnl.updateUI();
            cmds = 0;
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

    public final List<Area> getAreas(){
        return areas;
    }
    
    public Area getArea(AIFloat3 pos) {
        return map[map.length * (int) pos.x / mwidth][map[0].length * (int) pos.z / mheight];
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

    public class Area {

        public final int x, y;
        private AIFloat3 pos;
        private Owner owner;
        private Zone zoneCache;
        private int lastCache = -111;

        public Area(int x, int y) {
            this.x = x;
            this.y = y;
            this.pos = new AIFloat3((x + 0.5f) * mwidth / map.length, 0, (y + 0.5f) * mheight / map[0].length);
            this.pos.y = clbk.getMap().getElevationAt(pos.x, pos.z);
        }

        public boolean equals(Area a){
            return x == a.x && y == a.y;
        }
        
        private void setOwner(Owner o) {
            owner = o;
        }

        public Area getHighestArea(UnitDef building, float maxDistance) {
            int rw = (int) Math.floor(map.length * maxDistance / mwidth);
            int rh = (int) Math.floor(map[0].length * maxDistance / mheight);
            float h, maxh = 0;
            Area best = null;

            for (int x = Math.max(0,this.x - rw); x <= Math.min(map.length-1,this.x + rw); x++) {
                for (int y = Math.max(0,this.y - rh); y <= Math.min(map[0].length-1,this.y + rh); y++) {
                    h = clbk.getMap().getElevationAt(map[x][y].getPos().x, map[x][y].getPos().z);
                    if (h > maxh && clbk.getMap().isPossibleToBuildAt(building, map[x][y].getPos(), 0)) {
                        maxh = h;
                        best = map[x][y];
                    }
                }
            }
            return best;
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

        public boolean isVisible() {
            return command.radarManager.isInRadar(pos) || command.losManager.isInLos(pos);
        }
        
        List<Enemy> enemies;
        
        public final List<Enemy> getEnemies(){
            return enemies;
        }
        
        int lastDangerCache = -100;
        float dangerCache = 0;
        
        public float getDanger(){
            if (command.getCurrentFrame() - lastDangerCache > 60){
                enemies = new ArrayList();
                lastDangerCache = command.getCurrentFrame();
                dangerCache = 0;
                for (Enemy e : command.getEnemyUnits(false)){
                    float speed =  3*Math.max(30,e.getDef().getSpeed());
                    dangerCache += e.getDPS() * e.getHealth() /speed / Math.sqrt(2*Math.PI)
                            * Math.exp(-e.distanceTo(pos)*e.distanceTo(pos)/(speed*speed) );
                    if (getArea(e.getPos()).equals(this)){
                        enemies.add(e);
                    }
                }
            }
            return dangerCache;
        }
        
        int lastValueCache = -100;
        float valueCache = 0;
        
        public float getValue(){
            if (command.getCurrentFrame() - lastValueCache > 60){
                lastValueCache = command.getCurrentFrame();
                valueCache = 0;
                for (AIUnit u : command.getUnits()){
                    float speed =  8*Math.max(40,u.getUnit().getDef().getSpeed());
                    float val = u.getUnit().getDef().getCost(command.metal);
                    float dist = Math.max(0,u.distanceTo(pos));
                    valueCache +=  val/speed / Math.sqrt(2*Math.PI)
                            * Math.exp(-dist*dist/(speed*speed) );
                }
            }
            return valueCache;
        }

        public AIFloat3 getPos() {
            return new AIFloat3(pos);
        }

        public Zone getZone() {
            if (command.getCurrentFrame() - lastCache > 60) {
                Zone old = zoneCache;
                zoneCache = _getZone();
                if (old != zoneCache) 
                    for (AreaZoneChangeListener listener : zoneChangeListeners){
                        listener.areaZoneChange(this, old, zoneCache);
                    }
                lastCache = command.getCurrentFrame();
            }
            return zoneCache;
        }

        private Zone _getZone() {
            if (!isVisible() && owner == Owner.enemy) {
                return Zone.hostile;
            } else {
                owner = Owner.none;
            }
            if (command.defenseManager.isFortified(pos, Math.max(mwidth / map.length, mheight / map[0].length) / 2f)) {
                return Zone.fortified;
            }
            if (command.defenseManager.getDanger(pos, Math.max(mwidth / map.length, mheight / map[0].length) / 2f + 50) > 0) {
                return Zone.hostile;
            }
            return Zone.neutral;
        }
    }

    public class Mex {

        public final AIFloat3 pos;

        private Enemy enemy;

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

        public Owner getOwner() {
            if (enemy != null) {
                return Owner.enemy;
            }
            if (!clbk.getMap().isPossibleToBuildAt(clbk.getUnitDefByName("cormex"), pos, 0)) {
                return Owner.own;
            }
            return Owner.none;
        }

        public float distanceTo(AIFloat3 trg) {
            AIFloat3 pos = new AIFloat3(this.pos);
            pos.sub(trg);
            return pos.length();
        }

        public BuildTask createBuildTask(TaskIssuer t) {
            if (!clbk.getMap().isPossibleToBuildAt(clbk.getUnitDefByName("cormex"), pos, 0)) {
                return null;
            }
            return new BuildTask(clbk.getUnitDefByName("cormex"), pos, t, clbk, command, 0);
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
                    maxDanger = Math.max(maxDanger,map[x][y].getDanger());
                    maxValue = Math.max(maxValue,map[x][y].getValue());
                    maxImportance = Math.max(maxImportance,map[x][y].getValue()*map[x][y].getDanger());
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
                            g.setColor(new Color(100, 100, 100));
                            stringcol = Color.white;
                            break;
                        case fortified:
                            g.setColor(new Color(50, 50, 50));
                            stringcol = Color.white;
                            break;
                        case front:
                            g.setColor(new Color(255, 255, 50));
                            stringcol = Color.black;
                            break;
                        case neutral:
                            g.setColor(new Color(200, 200, 200));
                            stringcol = Color.white;
                            break;
                    }
                    if (!command.losManager.isInLos(map[x][y].getPos())) {
                        g.setColor(g.getColor().darker());
                        if (!command.radarManager.isInRadar(map[x][y].getPos())) g.setColor(g.getColor().darker());
                    }
                    float[] hsb = new float[3];
                    Color.RGBtoHSB(g.getColor().getRed(), g.getColor().getGreen(), g.getColor().getBlue(),hsb );
                    float danger = map[x][y].getDanger() / maxDanger;
                    float value = map[x][y].getValue() / maxValue;
                    hsb[0] = 0;
                    hsb[1] = map[x][y].getDanger()*map[x][y].getValue()/maxImportance;
                    g.setColor(new Color(Color.HSBtoRGB(hsb[0], hsb[1], hsb[2])));
                    g.fillRect((int) Math.round(x * w), (int) Math.round(y * h), (int)w+1, (int) h+1);
                    //g.setColor(Color.darkGray);
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
            for (Enemy e : command.getEnemyUnitsIn(new AIFloat3(), 10000)) {
                Graphics2D g2d = (Graphics2D) g;
                FontMetrics fm = g2d.getFontMetrics();
                Rectangle2D r = fm.getStringBounds(e.getDef().getHumanName(), g2d);
                int x = (int) Math.round(e.getPos().x * getWidth() / mwidth) - (int) r.getWidth() / 2;
                int y = (int) Math.round(e.getPos().z * getHeight() / mheight) - (int) r.getHeight() / 2 + fm.getAscent();

                g.setColor(Color.black);
                g.fillRect(x, y - fm.getAscent(), (int) Math.round(r.getWidth()), (int) Math.round(r.getHeight()));

                g.setColor(Color.orange);
                g.drawString(e.getDef().getHumanName(), x, y);
            }

        }

    }

}

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package zkcbai.helpers;

import com.springrts.ai.oo.AIFloat3;
import com.springrts.ai.oo.clb.OOAICallback;
import java.awt.Color;
import java.awt.Graphics;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.JFrame;
import javax.swing.JPanel;
import zkcbai.Command;
import zkcbai.UnitDestroyedListener;
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

    private JFrame frm;
    private JPanel pnl;

    private List<Mex> mexes;

    private final int mwidth;
    private final int mheight;

    public ZoneManager(Command cmd, OOAICallback clbk) {
        super(cmd, clbk);
        mwidth = clbk.getMap().getWidth() * 8;
        mheight = clbk.getMap().getHeight() * 8;
        map = new Area[50][50];
        for (int x = 0; x < map.length; x++) {
            for (int y = 0; y < map[x].length; y++) {
                map[x][y] = new Area(x, y);
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
            pnl.updateUI();
        }
    }

    public Mex getNearestMex(AIFloat3 pos) {
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
    public void unitDestroyed(AIUnit u) {

    }

    @Override
    public void unitDestroyed(Enemy e) {
        if (e.getDef().getName().equals("cormex")) {
            for (Mex m : mexes) {
                m.removeEnemyMex(e);
            }
        }
    }

    public enum Zone {

        own, front, noMansLand, hostile
    }

    public enum Owner {

        own, none, enemy
    }

    public class Area {

        public final int x, y;
        private AIFloat3 pos;
        private Owner owner;

        public Area(int x, int y) {
            this.x = x;
            this.y = y;
            this.pos = new AIFloat3(x * mwidth / map.length, 0, y * mheight / map[0].length);
            this.pos.y = clbk.getMap().getElevationAt(pos.x, pos.z);
        }
        
        private void setOwner(Owner o){
            owner = o;
        }

        public Area getNearestInvisibleArea() {
            for (int radius = 0; radius < Math.max(map.length, map[0].length); radius++) {
                command.debug(radius + " radius from " + x + "|" + y + " == " + pos.toString());
                int x, y;
                x = this.x - radius;
                for (y = this.y - radius; y <= this.y + radius; y++) {
                    command.debug("checking " + x + "|" + y);
                    if (x >= 0 && y >= 0 && x < map.length && y < map[0].length && !map[x][y].isVisible()) {
                        return map[x][y];
                    }
                }
                x = this.x + radius;
                for (y = this.y - radius; y <= this.y + radius; y++) {
                    command.debug("checking " + x + "|" + y);
                    if (x >= 0 && y >= 0 && x < map.length && y < map[0].length && !map[x][y].isVisible()) {
                        return map[x][y];
                    }
                }
                y = this.y - radius;
                for (x = this.x - radius; x <= this.x + radius; x++) {
                    command.debug("checking " + x + "|" + y);
                    if (x >= 0 && y >= 0 && x < map.length && y < map[0].length && !map[x][y].isVisible()) {
                        return map[x][y];
                    }
                }
                y = this.y + radius;
                for (x = this.x - radius; x <= this.x + radius; x++) {
                    command.debug("checking " + x + "|" + y);
                    if (x >= 0 && y >= 0 && x < map.length && y < map[0].length && !map[x][y].isVisible()) {
                        return map[x][y];
                    }
                }
            }
            return map[(int) (Math.random() * map.length)][(int) (Math.random() * map[0].length)];
        }

        public boolean isVisible() {
            return command.radarManager.isInRadar(pos) || command.losManager.isInLos(pos);
        }

        public AIFloat3 getPos() {
            command.debug(pos.toString() + " == " + new AIFloat3(pos).toString());
            return new AIFloat3(pos);
        }

        public Zone getZone() {
            if (!isVisible() && owner==Owner.enemy) return Zone.hostile;
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
            if (this.enemy.equals(e)) {
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
            float min = Float.MAX_VALUE;
            float max = Float.MIN_VALUE;

            float w = getWidth() / (float) map.length;
            float h = getHeight() / (float) map[0].length;

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
                            g.setColor(new Color(255, 0, 0));
                            stringcol = Color.white;
                            break;
                        case front:
                            g.setColor(new Color(255, 255, 50));
                            stringcol = Color.black;
                            break;
                        case noMansLand:
                            g.setColor(new Color(200, 200, 200));
                            stringcol = Color.white;
                            break;
                    }
                    g.fillRect((int) Math.round(x * w), (int) Math.round(y * h), (int) Math.round(w), (int) Math.round(h));
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

        }

    }

}

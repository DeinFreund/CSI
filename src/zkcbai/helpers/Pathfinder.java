/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package zkcbai.helpers;

import com.springrts.ai.oo.AIFloat3;
import com.springrts.ai.oo.clb.OOAICallback;
import com.springrts.ai.oo.clb.UnitDef;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import zkcbai.Command;
import zkcbai.unitHandlers.units.AIUnit;
import zkcbai.unitHandlers.units.Enemy;

/**
 *
 * @author User
 */
public class Pathfinder extends Helper {

    public Pathfinder(Command cmd, OOAICallback clbk) {
        super(cmd, clbk);
        mwidth = clbk.getMap().getWidth() * 8;
        mheight = clbk.getMap().getHeight() * 8;
        smwidth = (int) (mwidth / mapRes);
        updateSlopeMap();
    }

    float mwidth, mheight;
    int smwidth;
    float[] slopeMap;
    private final static int mapCompression = 8;
    private final static int originalMapRes = 16;
    private final static int mapRes = originalMapRes * mapCompression;

    private void updateSlopeMap() {
        List<Float> map = clbk.getMap().getSlopeMap();
        slopeMap = new float[map.size() / mapCompression / mapCompression];
        for (int x = 0; x < mwidth / originalMapRes; x++) {
            for (int y = 0; y < mheight / originalMapRes; y++) {
                int cx = x / mapCompression;
                int cy = y / mapCompression;
                slopeMap[cy * smwidth + cx] = Math.max(slopeMap[cy * smwidth + cx], map.get(y * (smwidth * mapCompression) + x));
            }
        }
    }

    private Map<CostSupplier, float[]> costSupplierCosts = new HashMap();
    private Map<CostSupplier, int[]> costSupplierLastUpdate = new HashMap();

    private float getCachedCost(CostSupplier supplier, float slope, float maxSlope, int pos) {
        if (!costSupplierCosts.containsKey(supplier)) {
            costSupplierCosts.put(supplier, new float[slopeMap.length]);
            costSupplierLastUpdate.put(supplier, new int[slopeMap.length]);
        }
        float[] costs = costSupplierCosts.get(supplier);
        int[] lastUpdate = costSupplierLastUpdate.get(supplier);
        if (command.getCurrentFrame() - lastUpdate[pos] > 15) {
            lastUpdate[pos] = command.getCurrentFrame();
            costs[pos] = supplier.getCost(slope, maxSlope, toAIFloat3(pos));
        }
        return costs[pos];
    }

    public boolean isReachable(AIFloat3 target, AIFloat3 start, float maxSlope){
        return findPath(start, target, maxSlope, FAST_PATH).size() > 1;
    }
    
    /**
     * Finds the cheapest path between two arbitrary positions using the A*
     * algorithm.
     *
     * @param start
     * @param target
     * @param maxSlope Maximum slope that can be travelled on. 0 &lt; maxslope
     * &lt; 1
     * @param costs Class implementing CostSupplier
     * @return Path as List of AIFloat3. If list.size() &lt; 2 no valid path was
     * @see #FAST_PATH
     * @see #RAIDER_PATH
     * @see #AVOID_ENEMIES found.
     *
     */
    public Deque<AIFloat3> findPath(AIFloat3 start, AIFloat3 target, float maxSlope, CostSupplier costs) {

//        command.debug("starting pathfinder");
//        command.mark(start, "start");
//        command.mark(target, "target");
        long time = System.currentTimeMillis();
        int startPos = (int) (target.z / mapRes) * smwidth + (int) (target.x / mapRes); //reverse to return in right order when traversing backwards
        int targetPos = (int) (start.z / mapRes) * smwidth + (int) (start.x / mapRes);
        int[] offset = new int[]{-1, 1, smwidth, -smwidth};// smwidth + 1, smwidth - 1, -smwidth + 1, -smwidth - 1};
        float[] offsetCostMod = new float[]{1, 1, 1, 1, 1.5f, 1.5f, 1.5f, 1.5f};

        Deque<AIFloat3> result = new ArrayDeque();

        Comparator<pqEntry> pqComp = new Comparator<pqEntry>() {

            @Override
            public int compare(pqEntry t, pqEntry t1) {
                if (t == null && t1 == null) {
                    return 0;
                }
                if (t == null) {
                    return -1;
                }
                if (t1 == null) {
                    return 1;
                }
                return (int) Math.signum(t.cost - t1.cost);
            }

        };

        PriorityQueue<pqEntry> pq = new PriorityQueue(1, pqComp);
        pq.add(new pqEntry(getHeuristic(startPos, targetPos), 0, startPos));

        float[] minCost = new float[slopeMap.length];
        int[] pathTo = new int[slopeMap.length];
        for (int i = 0; i < minCost.length; i++) {
            minCost[i] = Float.MAX_VALUE;
        }
        minCost[startPos] = 0;

        int pos;
        float cost;

        while (true) {

            do {
                if (pq.isEmpty()) {
                    command.debug("pathfinder didn't find path");
                    /*clbk.getMap().getDrawer().addPoint(start, "start");
                    clbk.getMap().getDrawer().addPoint(target, "target");
                    clbk.getMap().getDrawer().addLine(start, target );*/
                    return result;
                }
                pos = pq.peek().pos;
                cost = pq.poll().realCost;
            } while (cost > minCost[pos]);
            if (pos == targetPos) {
                break;
            }

            for (int i = 0; i < offset.length; i++) {
                if (pos % (smwidth) == 0) {
                    continue;
                }
                if ((pos + 1) % (smwidth) == 0) {
                    continue;
                }
                if (inBounds(pos + offset[i], minCost.length)
                        && cost + offsetCostMod[i] * getCachedCost(costs, slopeMap[pos + offset[i]], maxSlope, (pos + offset[i])) < minCost[pos + offset[i]]) {

                    pathTo[pos + offset[i]] = pos;
                    minCost[pos + offset[i]] = cost + offsetCostMod[i] * getCachedCost(costs, slopeMap[pos + offset[i]], maxSlope, (pos + offset[i]));
                    pq.add(new pqEntry(getHeuristic(pos + offset[i], targetPos) + minCost[pos + offset[i]],
                            minCost[pos + offset[i]], pos + offset[i]));
                }
            }
        }

        pos = targetPos;
        while (pos != startPos) {
            //clbk.getMap().getDrawer().addLine(toAIFloat3(pos), toAIFloat3(pathTo[pos]));
            result.add(toAIFloat3(pos));
            pos = pathTo[pos];
        }
        result.add(target);
        result.add(target);//add twice to confirm path
        time = System.currentTimeMillis() - time;
        if (time > 10) {
            command.debug("pathfinder took " + time + "ms");
        }
        return result;

    }

    private float getHeuristic(int start, int trg) {
        return Math.abs(start % smwidth - trg % smwidth) + Math.abs(start / smwidth - trg / smwidth);//manhattan distance only works without diagonal paths
        //return (float) Math.sqrt((start % smwidth - trg % smwidth) * (start % smwidth - trg % smwidth) + (start / smwidth - trg / smwidth) * (start / smwidth - trg / smwidth));
    }

    private AIFloat3 toAIFloat3(int pos) {
        AIFloat3 ret = new AIFloat3(mapRes * (pos % (smwidth)), 0, mapRes * (pos / (smwidth)));
        ret.y = clbk.getMap().getElevationAt(ret.x, ret.z);
        return ret;
    }

    private float getCost(float slope, float maxSlope) {
        if (slope > maxSlope) {
            return Float.MAX_VALUE;
        }
        return 10 * (slope / maxSlope + ((slope > maxSlope) ? (1000): (0))) + 1;
    }

    /**
     * Fastest path to target (not shortest)
     */
    public final CostSupplier FAST_PATH = new CostSupplier() {

        @Override
        public float getCost(float slope, float maxSlope, AIFloat3 pos) {
            if (slope > maxSlope) {
                return Float.MAX_VALUE;
            }
            return 10 * (slope / maxSlope + ((slope > maxSlope) ? (1000): (0))) + 1;
        }
    };
    /**
     * Fastest path to target while avoiding riot units
     */
    public final CostSupplier RAIDER_PATH = new CostSupplier() {

        @Override
        public float getCost(float slope, float maxSlope, AIFloat3 pos) {
            if (slope > maxSlope) {
                return Float.MAX_VALUE;
            }
            return 10 * (slope / maxSlope + ((slope > maxSlope) ? (1000): (0))) + 200 * command.defenseManager.getRaiderAccessibilityCost(pos) + 1;
        }
    };
    /**
     * Fastest path to target while  TODO
     */
    public final CostSupplier ASSAULT_PATH = new CostSupplier() {

        @Override
        public float getCost(float slope, float maxSlope, AIFloat3 pos) {
            if (slope > maxSlope) {
                return Float.MAX_VALUE;
            }
            return 10 * (slope / maxSlope + ((slope > maxSlope) ? (1000): (0))) + 200 * command.defenseManager.getAssaultAccessibilityCost(pos) + 1;
        }
    };
    /**
     * Fastest path to target while avoiding enemies that are able to attack
     */
    public final CostSupplier AVOID_ENEMIES = new CostSupplier() {

        @Override
        public float getCost(float slope, float maxSlope, AIFloat3 pos) {
            if (slope > maxSlope) {
                return Float.MAX_VALUE;
            }
            return 10 * (slope / maxSlope + ((slope > maxSlope) ? (1000): (0))) + 0.04f * command.defenseManager.getGeneralDanger(pos) + 1;
        }
    };

    private boolean inBounds(int num, int max) {
        return num < max && num >= 0;
    }

    private class pqEntry {

        final float cost;
        final float realCost;
        final int pos;

        public pqEntry(float cost, float realCost, int pos) {
            this.cost = cost;
            this.pos = pos;
            this.realCost = realCost;
        }
    }

    private float convertMaxSlope(float maxSlope) {
        return 1f - (float) Math.cos(maxSlope * 1.5 / 180 * Math.PI);
    }

    @Override
    public void unitFinished(AIUnit u) {
    }

    @Override
    public void update(int frame) {
    }

    /**
     *
     * @param start
     * @param target
     * @param maxSlope
     * @return Path as list of AIFloat3
     * @deprecated Finds the shortest path using hardcoded costs. A CostSupplier
     * should be used instead.
     */
    @Deprecated
    public List<AIFloat3> findPath(AIFloat3 start, AIFloat3 target, float maxSlope) {

        //command.debug("maxSlope: " + maxSlope);
        long time = System.currentTimeMillis();
        //maxSlope = convertMaxSlope(maxSlope);
        int startPos = (int) (target.z / mapRes) * smwidth + (int) (target.x / mapRes); //reverse to return in right order when traversing backwards
        int targetPos = (int) (start.z / mapRes) * smwidth + (int) (start.x / mapRes);
        int[] offset = new int[]{-1, 1, smwidth, -smwidth, smwidth + 1, smwidth - 1, -smwidth + 1, -smwidth - 1};
        float[] offsetCostMod = new float[]{1, 1, 1, 1, 1.5f, 1.5f, 1.5f, 1.5f};

//        command.mark(start, "start");
//        command.mark(target, "target");
//        command.mark(toAIFloat3(startPos), "start" + startPos);
//        command.mark(toAIFloat3(targetPos), "target" + targetPos);
        Comparator<pqEntry> pqComp = new Comparator<pqEntry>() {

            @Override
            public int compare(pqEntry t, pqEntry t1) {
                if (t == null && t1 == null) {
                    return 0;
                }
                if (t == null) {
                    return -1;
                }
                if (t1 == null) {
                    return 1;
                }
                return (int) Math.signum(t.cost - t1.cost);
            }

        };

        PriorityQueue<pqEntry> pq = new PriorityQueue(1, pqComp);
        pq.add(new pqEntry(getHeuristic(startPos, targetPos), 0, startPos));

        float[] minCost = new float[slopeMap.length];
        int[] pathTo = new int[slopeMap.length];
        for (int i = 0; i < minCost.length; i++) {
            minCost[i] = Float.MAX_VALUE;
        }
        minCost[startPos] = 0;

        int pos;
        float cost;

        while (true) {
            
            do {
                if (pq.isEmpty()) {
                    command.debug("pathfinder didn't find path");
                    /*clbk.getMap().getDrawer().addPoint(start, "start");
                    clbk.getMap().getDrawer().addPoint(target, "target");
                    clbk.getMap().getDrawer().addLine(start, target );*/
                    return new ArrayList();
                }
                pos = pq.peek().pos;
                cost = pq.poll().realCost;
            } while (cost > minCost[pos]);
            if (pos == targetPos) {
                break;
            }

            //clbk.getMap().getDrawer().addLine(toAIFloat3(pos), toAIFloat3(pathTo[pos]));
            for (int i = 0; i < offset.length; i++) {
                if (pos % (smwidth) == 0 && offset[i] % smwidth == -1) {
                    continue;
                }
                if ((pos + 1) % (smwidth) == 0 && offset[i] % smwidth == 1) {
                    continue;
                }
                if (inBounds(pos + offset[i], minCost.length)
                        && cost + offsetCostMod[i] * getCost(slopeMap[pos + offset[i]], maxSlope) < minCost[pos + offset[i]]) {

                    pathTo[pos + offset[i]] = pos;
                    minCost[pos + offset[i]] = cost + offsetCostMod[i] * getCost(slopeMap[pos + offset[i]], maxSlope);
                    pq.add(new pqEntry(getHeuristic(pos + offset[i], targetPos) + minCost[pos + offset[i]],
                            minCost[pos + offset[i]], pos + offset[i]));
                }
            }
        }

        pos = targetPos;
        List<AIFloat3> result = new ArrayList();
        while (pos != startPos) {
            //clbk.getMap().getDrawer().addLine(toAIFloat3(pos), toAIFloat3(pathTo[pos]));
            result.add(toAIFloat3(pos));
            pos = pathTo[pos];
        }
        time = System.currentTimeMillis() - time;
        command.debug("pathfinder took " + time + "ms");
        return result;

    }
    
    

}

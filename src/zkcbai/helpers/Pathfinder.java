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
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;
import zkcbai.Command;
import static zkcbai.helpers.Helper.command;
import zkcbai.helpers.ZoneManager.Area;
import zkcbai.helpers.ZoneManager.Area.Connection;
import zkcbai.unitHandlers.units.AIUnit;

/**
 *
 * @author User
 */
public final class Pathfinder extends Helper {

    public Pathfinder(Command cmd, OOAICallback clbk) {
        super(cmd, clbk);
        mwidth = clbk.getMap().getWidth() * 8;
        mheight = clbk.getMap().getHeight() * 8;
        smwidth = (int) (mwidth / mapRes);
        updateSlopeMap();
        backgroundThread = new Thread(new BackgroundPathfinder(this));
        backgroundThread.start();
    }

    float mwidth, mheight;
    int smwidth;
    float[] slopeMap;
    protected Thread backgroundThread;
    final static int mapCompression = 1;
    final static int originalMapRes = 16;
    final static int mapRes = originalMapRes * mapCompression;

    float minCostPerElmo = Float.MAX_VALUE; //optimal ratio of elmo distance to precached distance for astar heuristic

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

    private float getCachedCost(CostSupplier supplier, Area pos) {
        if (!costSupplierCosts.containsKey(supplier)) {
            costSupplierCosts.put(supplier, new float[command.areaManager.getAreas().size()]);
            costSupplierLastUpdate.put(supplier, new int[command.areaManager.getAreas().size()]);
        }
        float[] costs = costSupplierCosts.get(supplier);
        int[] lastUpdate = costSupplierLastUpdate.get(supplier);
        if (command.getCurrentFrame() - lastUpdate[pos.index] > 32) {
            lastUpdate[pos.index] = command.getCurrentFrame();
            costs[pos.index] = supplier.getCost(pos);
        }
        return costs[pos.index];
    }

    public boolean isReachable(AIFloat3 target, AIFloat3 start, float maxSlope) {
        return findPath(start, target, maxSlope, FAST_PATH).size() > 1;
    }

    private Queue<PathfinderRequest> requests = new LinkedBlockingQueue<>();

    public Queue<PathfinderRequest> getPathfinderRequests() {
        return requests;
    }

    /**
     * Requests the cheapest path between two arbitrary positions using the A* algorithm.
     *
     * @param start
     * @param target
     * @param maxSlope Maximum slope that can be traveled on. 0 &lt; maxslope &lt; 1
     * @param costs Class implementing CostSupplier
     * @param listener will be called after path has been calculated
     * @see #FAST_PATH
     * @see #RAIDER_PATH
     * @see #AVOID_ENEMIES found.
     *
     */
    public void requestPath(AIFloat3 start, AIFloat3 target, float maxSlope, CostSupplier costs, PathfindingCompleteListener listener) {
        requestPath(start, target, MovementType.getMovementType(maxSlope), costs, false, listener);
    }

    /**
     * Requests the cheapest path between two arbitrary positions using the A* algorithm.
     *
     * @param start
     * @param target
     * @param mt Movement type of unit that is traveling &lt; 1
     * @param costs Class implementing CostSupplier
     * @param listener will be called after path has been calculated
     * @see #FAST_PATH
     * @see #RAIDER_PATH
     * @see #AVOID_ENEMIES found.
     *
     */
    public void requestPath(AIFloat3 start, AIFloat3 target, MovementType mt, CostSupplier costs, PathfindingCompleteListener listener) {
        requestPath(start, target, mt, costs, false, listener);
    }

    /**
     * Requests the cheapest path between two arbitrary positions using the A* algorithm.
     *
     * @param start
     * @param target
     * @param mt Maximum slope that can be travelled on. 0 &lt; maxslope &lt; 1
     * @param costs Class implementing CostSupplier
     * @param markReachable if this is set, all reached areas will be marked as
     * @param listener will be called after path has been calculated such WITHOUT EFFECT
     * @see #FAST_PATH
     * @see #RAIDER_PATH
     * @see #AVOID_ENEMIES found.
     *
     */
    public void requestPath(AIFloat3 start, AIFloat3 target, MovementType mt, CostSupplier costs, boolean markReachable, PathfindingCompleteListener listener) {
        //listener.foundPath(findPath(start, target, mt, costs, markReachable));
        requests.add(new PathfinderRequest(start, target, mt, costs, markReachable, listener));
    }

    /**
     * Finds the cheapest path between two arbitrary positions using the A* algorithm.
     *
     * @param start
     * @param target
     * @param maxSlope Maximum slope that can be traveled on. 0 &lt; maxslope &lt; 1
     * @param costs Class implementing CostSupplier
     * @return Path as List of AIFloat3. If list.size() &lt; 2 no valid path was
     * @see #FAST_PATH
     * @see #RAIDER_PATH
     * @see #AVOID_ENEMIES found.
     *
     */
    public Deque<AIFloat3> findPath(AIFloat3 start, AIFloat3 target, float maxSlope, CostSupplier costs) {
        return findPath(start, target, MovementType.getMovementType(maxSlope), costs, false, false);
    }

    /**
     * Finds the cheapest path between two arbitrary positions using the A* algorithm.
     *
     * @param start
     * @param target
     * @param mt Movement type of unit that is traveling &lt; 1
     * @param costs Class implementing CostSupplier
     * @return Path as List of AIFloat3. If list.size() &lt; 2 no valid path was
     * @see #FAST_PATH
     * @see #RAIDER_PATH
     * @see #AVOID_ENEMIES found.
     *
     */
    public Deque<AIFloat3> findPath(AIFloat3 start, AIFloat3 target, MovementType mt, CostSupplier costs) {
        return findPath(start, target, mt, costs, false, false);
    }

    /**
     * Finds the cheapest path between two arbitrary positions using the A* algorithm.
     *
     * @param start
     * @param target
     * @param movementType movement Type indicating passable terrain
     * @param costs Class implementing CostSupplier
     * @param markReachable if this is set, all reached areas will be marked as such WITHOUT EFFECT
     * @return Path as List of AIFloat3. If list.size() &lt; 2 no valid path was
     * @see #FAST_PATH
     * @see #RAIDER_PATH
     * @see #AVOID_ENEMIES found.
     *
     */
    public Deque<AIFloat3> findPath(AIFloat3 start, AIFloat3 target, final MovementType movementType, final CostSupplier costs, boolean markReachable, boolean approximatePath) {
        //command.debug("async pathfinder using " + costs.getClass().getName(), false);
        long time = System.currentTimeMillis();
        Comparator<pqEntry2> pqComp = new Comparator<pqEntry2>() {

            @Override
            public int compare(pqEntry2 t, pqEntry2 t1) {
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

        Area startPos = command.areaManager.getArea(start).getNearestArea(new AreaChecker() {

            @Override
            public boolean checkArea(Area a) {
                return !a.getConnections(movementType).isEmpty();
            }
        });
        Area targetPos = command.areaManager.getArea(target);

        if (startPos == null) {
            throw new AssertionError("No areas to start from found");
        }

        Map<Area, Float> minCost = new HashMap();
        Map<Area, Area> prev = new HashMap();
        Set<Area> visited = new HashSet();

        minCost.put(startPos, 0f);
        prev.put(startPos, startPos);

        PriorityQueue<pqEntry2> pq = new PriorityQueue(1, pqComp);
        pq.add(new pqEntry2(startPos.distanceTo(targetPos.getPos()), 0, startPos));
        int evaluated = 0;
        int evareas = 0;
        while (!pq.isEmpty()) {
            Area pos = pq.peek().pos;
            float cost = pq.poll().realCost;
            if (minCost.get(pos) < cost) {
                continue;
            }
            if (pos.equals(targetPos) || approximatePath && pos.getNeighbours().contains(targetPos)) {
                targetPos = pos;
                break;
            }

            if (visited.contains(pos)) {
                throw new RuntimeException("nononono" + (float) smwidth / mwidth);
            }
            visited.add(pos);
            evareas++;
            for (Connection c : pos.getConnections(movementType)) {
                evaluated++;
                float newcost = c.length + cost + getCachedCost(costs, c.endpoint);
                //if (costs.getCost(c.endpoint) < 0) throw new RuntimeException("negative length");
                if ((!minCost.containsKey(c.endpoint) || newcost < minCost.get(c.endpoint))) {
                    minCost.put(c.endpoint, newcost);
                    prev.put(c.endpoint, pos);
                    pq.add(new pqEntry2(newcost + minCostPerElmo * c.endpoint.distanceTo(targetPos.getPos()), newcost, c.endpoint));
                }
            }
        }
        Deque<AIFloat3> res = new LinkedList<>();
        if (!minCost.containsKey(targetPos)) {
            if (!approximatePath) {
                return findPath(start, target, movementType, costs, markReachable, true);
            } else {
                //command.mark(start, "start (impossible path)");
                //command.mark(target, "end (impossible path)");
                command.debug("Impossible path");
                command.debugStackTrace();
                res.add(target);
                return res;
            }
        }
        Area pos = targetPos;
        while (pos != prev.get(pos)) {
            pos.addDebugConnection(prev.get(pos), Color.yellow, command.getCurrentFrame() + 30);
            res.addFirst(pos.getPos());
            pos = prev.get(pos);
        }
        res.addFirst(pos.getPos());
        time = System.currentTimeMillis() - time;
        if (time > 10) {
            command.debug("Area Pathfinder took " + time + "ms evaluating " + evaluated + " connections in " + evareas + " areas");
            command.debug("From " + startPos.x + "|" + startPos.y + " to " + targetPos.x + "|" + targetPos.y);
            if (time > 6000) {
                throw new AssertionError("Time limit exceeded");
            }
        }
        return res;
    }

    private static enum _MovementType {

        bot, spider, vehicle, air;
    }

    public static enum MovementType {

        bot(_MovementType.bot),
        spider(_MovementType.spider),
        vehicle(_MovementType.vehicle),
        air(_MovementType.air);

        private final _MovementType type;

        private MovementType(final _MovementType type) {
            this.type = type;
        }

        public float getMaxSlope() {
            return getMaxSlope(this.type);
        }

        public static MovementType getMovementType(UnitDef ud) {
            if (ud.isAbleToFly()) {
                return MovementType.air;
            }
            return getMovementType(ud.getMoveData().getMaxSlope());
        }

        public static MovementType getMovementType(float targetSlope) {
            float maxSlope = -1;
            MovementType maxType = null;
            for (MovementType mt : MovementType.values()) {
                if (mt.getMaxSlope() > maxSlope && mt.getMaxSlope() <= targetSlope + 0.0001) {
                    maxSlope = mt.getMaxSlope();
                    maxType = mt;
                }
            }
            return maxType;
        }

        private float getMaxSlope(_MovementType t) {
            switch (t) {
                case bot:
                    return command.getCallback().getUnitDefByName("armpw").getMoveData().getMaxSlope();
                case spider:
                    return command.getCallback().getUnitDefByName("armflea").getMoveData().getMaxSlope();
                case vehicle:
                    return command.getCallback().getUnitDefByName("corgator").getMoveData().getMaxSlope();
                case air:
                    return 1f;
                default:
                    throw new UnsupportedOperationException("unimplemented movement type");
            }
        }
    }

    public float getDistance(Float3 start, Float3 target) {
        return (float) Math.sqrt((start.x - target.x) * (start.x - target.x) + (start.z - target.z) * (start.z - target.z));
    }

    /*
     public void precalcPath(Area startarea, MovementType movementType, Area targetarea) {
     //public void precalcPath(AIFloat3 start, float maxSlope, AIFloat3 target, float encradius) {

     //command.debug("starting pathfinder to " + target.toString());
     //command.debug("maxslop is " + maxSlope);
     float maxSlope = movementType.getMaxSlope();
     AIFloat3 start = startarea.getPos();
     AIFloat3 target = targetarea.getPos();
     float encradius = startarea.getEnclosingRadius();
     if (!(maxSlope > 0 && maxSlope <= 1)) {
     throw new RuntimeException("Invalid maxSlope: " + maxSlope);
     }

     //command.mark(start,"markreachable is " + markReachable);
     //        command.mark(start, "start");
     //command.mark(target, "target");
     long time = System.currentTimeMillis();
     int startPos = (int) (target.z / mapRes) * smwidth + (int) (target.x / mapRes); //reverse to return in right order when traversing backwards
     int targetPos = (int) (start.z / mapRes) * smwidth + (int) (start.x / mapRes);
     int[] offset = new int[]{-1, 1, smwidth, -smwidth, smwidth + 1, smwidth - 1, -smwidth + 1, -smwidth - 1};
     float[] offsetCostMod = new float[]{1, 1, 1, 1, 1.42f, 1.42f, 1.42f, 1.42f};

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

     // command.debug("pathfinder iteration");
     do {
     if (pq.isEmpty()) {
     result.add(target);
     //command.debug("pathfinder couldnt find target");
     return;
     }
     pos = pq.peek().pos;
     cost = pq.poll().realCost;
     //if (cost > 1e6f) command.mark(toAIFloat3(pos), "unreachable with " + maxSlope);
     } while (cost > minCost[pos] || cost >= 1e6f);
     if (pos == targetPos) {//breaks but shouldnt

     //command.debug("pathfinder reached target");
     //command.mark(new AIFloat3(),"pathfinder reached target");
     break;
     }

     for (int i = 0; i < offset.length; i++) {
     if (pos % (smwidth) == 0 && offset[i] % smwidth != 0) {
     //command.mark(toAIFloat3(pos), "stopping");
     continue;
     }
     if ((pos + 1) % (smwidth) == 0 && offset[i] % smwidth != 0) {
     //command.mark(toAIFloat3(pos), "stopping");
     continue;
     }
     //                command.debug(inBounds(pos + offset[i], minCost.length)
     //                        + "&&" + cost + "+" + offsetCostMod[i] + "*" + getCachedCost(costs, slopeMap[pos + offset[i]], maxSlope, (pos + offset[i])) +" <"+
     //                        minCost[pos + offset[i]]);
     if (inBounds(pos + offset[i], minCost.length)
     && cost + offsetCostMod[i] * getCost(slopeMap[pos + offset[i]], maxSlope) < minCost[pos + offset[i]]
     && (getDistance(start, toAIFloat3(pos + offset[i])) < 1.5f * encradius
     || getDistance(target, toAIFloat3(pos + offset[i])) < 1.5f * encradius)) {

     pathTo[pos + offset[i]] = pos;
     minCost[pos + offset[i]] = cost + offsetCostMod[i] * getCost(slopeMap[pos + offset[i]], maxSlope);
     pq.add(new pqEntry(getHeuristic(pos + offset[i], targetPos) + minCost[pos + offset[i]],
     minCost[pos + offset[i]], pos + offset[i]));
     //command.mark(toAIFloat3(pos+offset[i]), "for " + (getHeuristic(pos + offset[i], targetPos) + minCost[pos + offset[i]]));
     }
     }
     }

     float totalcost = minCost[targetPos];
     //startarea.queueConnection(targetarea, totalcost, movementType);
     //targetarea.queueConnection(startarea, totalcost, movementType);

     time = System.currentTimeMillis() - time;
     if (time > 10) {
     //command.debug("pathfinder took " + time + "ms");
     }
     return;

     }*/

    float getHeuristic(int start, int trg) {
        //return Math.abs(start % smwidth - trg % smwidth) + Math.abs(start / smwidth - trg / smwidth);//manhattan distance only works without diagonal paths
        return (float) Math.sqrt((start % smwidth - trg % smwidth) * (start % smwidth - trg % smwidth) + (start / smwidth - trg / smwidth) * (start / smwidth - trg / smwidth));
    }

    Float3 toFloat3(int pos) {
        Float3 ret = new Float3(mapRes * (pos % (smwidth)), 0, mapRes * (pos / (smwidth)));
        ret.y = clbk.getMap().getElevationAt(ret.x, ret.z);
        return ret;
    }

    float getCost(float slope, float maxSlope) {
        if (slope > maxSlope) {
            return Float.MAX_VALUE;
        }
        return 10 * (slope / maxSlope + ((slope > maxSlope) ? (1e6f) : (0))) + 1;
    }

    /**
     * Fastest path to target (not shortest)
     */
    public final CostSupplier FAST_PATH = new CostSupplier() {

        @Override
        public float getCost(Area pos) {
            return 0;
        }
    };
    /**
     * Fastest path to target while avoiding riot units
     */
    public final CostSupplier RAIDER_PATH = new CostSupplier() {

        @Override
        public float getCost(Area pos) {
            return 20000 * command.defenseManager.getRaiderAccessibilityCost(pos.getPos());
        }
    };
    /**
     * Fastest path to target while TODO
     */
    public final CostSupplier ASSAULT_PATH = new CostSupplier() {

        @Override
        public float getCost(Area pos) {
            return 20000 * command.defenseManager.getAssaultAccessibilityCost(pos.getPos());
        }
    };
    /**
     * Fastest path to target while avoiding enemies that are able to attack
     */
    public final CostSupplier AVOID_ENEMIES = new CostSupplier() {

        @Override
        public float getCost(Area pos) {
            return 4f * command.defenseManager.getGeneralDanger(pos.getPos());
        }
    };
    /**
     * Fastest path to target while avoiding non flying enemies that are able to attack
     */
    public final CostSupplier AVOID_GROUND_ENEMIES = new CostSupplier() {

        @Override
        public float getCost(Area pos) {
            return 4f * command.defenseManager.getGeneralDanger(pos.getPos(), true, true);
        }
    };
    /**
     * Fastest path to target while avoiding antiair
     */
    public final CostSupplier AVOID_ANTIAIR = new CostSupplier() {

        @Override
        public float getCost(Area pos) {
            return 4f * pos.getEnemyAADPS(false);
        }
    };

    boolean inBounds(int num, int max) {
        return num < max && num >= 0;
    }

    static class pqEntry {

        final float cost;
        final float realCost;
        final int pos;

        public pqEntry(float cost, float realCost, int pos) {
            this.cost = cost;
            this.pos = pos;
            this.realCost = realCost;
        }
    }

    private class pqEntry2 {

        final float cost;
        final float realCost;
        final Area pos;

        public pqEntry2(float cost, float realCost, Area pos) {
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

//      /**
//     *
//     * @param start
//     * @param target
//     * @param maxSlope
//     * @return Path as list of AIFloat3
//     * @deprecated Finds the shortest path using hardcoded costs. A CostSupplier
//     * should be used instead.
//     */
    /*
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

     }*/
    public class PathfinderRequest {

        public final AIFloat3 start;
        public final AIFloat3 target;
        public final MovementType movementType;
        public final CostSupplier costs;
        public final boolean markReachable;
        public final PathfindingCompleteListener listener;

        public PathfinderRequest(AIFloat3 start, AIFloat3 target, MovementType mt, CostSupplier costs, boolean markReachable, PathfindingCompleteListener listener) {
            this.start = start;
            this.target = target;
            this.movementType = mt;
            this.costs = costs;
            this.markReachable = markReachable;
            this.listener = listener;
        }

    }

}

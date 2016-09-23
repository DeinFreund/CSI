/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package zkcbai.helpers;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.PriorityQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.swing.JPanel;
import zkcbai.Command;
import zkcbai.helpers.Pathfinder.pqEntry;

/**
 *
 * @author User
 */
public class PathPrecalculatorThread implements Runnable {

    Command command;
    int offset, modulo;
    PrecalcTask[] myTasks;

    public PathPrecalculatorThread(Command cmd, int offset, int modulo, PrecalcTask[] tasks) {
        this.command = cmd;
        this.offset = offset;
        this.modulo = modulo;
        this.myTasks = tasks;
    }

    protected static CountDownLatch countdown;
    protected static CountDownLatch queuingDone;
    protected static int threads = Runtime.getRuntime().availableProcessors();
    protected static int taskcnt = 0;
    protected static List< PrecalcTask> tasks[] = new ArrayList[threads];
    protected static float[] minCostPerElmo;

    public static void addPrecalcTask(ZoneManager.Area startarea, Pathfinder.MovementType movementType, ZoneManager.Area targetarea) {
        if (tasks[taskcnt % threads] == null) {
            tasks[taskcnt % threads] = new ArrayList<>();
        }
        tasks[taskcnt++ % threads].add(new PrecalcTask(startarea, movementType, targetarea));
    }

    public static void calculate(Command command, JPanel pnl) {
        command.debug("Precalculating paths on " + threads + " cores");

        long time = System.currentTimeMillis();
        Thread[] thread = new Thread[threads];
        minCostPerElmo = new float[threads];
        countdown = new CountDownLatch(threads);
        queuingDone = new CountDownLatch(threads);
        for (int i = 0; i < thread.length; i++) {
            minCostPerElmo[i] = Float.MAX_VALUE;
            thread[i] = new Thread(new PathPrecalculatorThread(command, i, threads, tasks[i].toArray(new PrecalcTask[tasks[i].size()])));
            thread[i].setPriority(Thread.MIN_PRIORITY);
            thread[i].start();
        }
        try {
            while (!countdown.await(25, TimeUnit.MILLISECONDS)) {
                if (pnl != null) {
                    pnl.updateUI();
                }
            }
        } catch (InterruptedException ex) {
            command.debug("Exception while calculating paths ", ex);
        }
        int chk = 0;
        /*for (int i = 0; i <8; i++){
         chk += sums[i];
         }*/

        for (int i = 0; i < thread.length; i++) {
            command.pathfinder.minCostPerElmo = Math.min(command.pathfinder.minCostPerElmo, minCostPerElmo[i]);
        }
        command.debug("Done. " + (System.currentTimeMillis() - time) / 1000.0 + "s" + chk);

    }
    float[] slopeMap;
    int mapRes;
    int smwidth;

    //static int[] sums = new int[8];
    @Override
    public void run() {
        try {
            slopeMap = new float[command.pathfinder.slopeMap.length];
            mapRes = command.pathfinder.mapRes;
            smwidth = command.pathfinder.smwidth;
            System.arraycopy(command.pathfinder.slopeMap, 0, slopeMap, 0, slopeMap.length);

            int index = 0;
            long initcounter = 0;
            long loopcounter = 0;
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
            float[] minCost = new float[slopeMap.length];
            command.debug("Initialized thread " + offset, false);
            while (index < myTasks.length) {
                //command.debug("thread " + offset + ": " + index);
                long time = System.nanoTime();
                PrecalcTask task = myTasks[index++];
                float maxSlope = task.movementType.getMaxSlope();
                Float3 start = task.start;
                Float3 target = task.target;
                float encradius = task.encradius;
                if (!(maxSlope > 0 && maxSlope <= 1)) {
                    throw new RuntimeException("Invalid maxSlope: " + maxSlope);
                }

                int startPos = (int) (target.z / mapRes) * smwidth + (int) (target.x / mapRes); //reverse to return in right order when traversing backwards
                int targetPos = (int) (start.z / mapRes) * smwidth + (int) (start.x / mapRes);
                int[] offset2 = new int[]{-1, 1, smwidth, -smwidth, smwidth + 1, smwidth - 1, -smwidth + 1, -smwidth - 1};
                int[] offset2check = new int[]{-1, 1, smwidth, -smwidth, smwidth, smwidth, -smwidth, -smwidth};
                float[] offsetCostMod = new float[]{1, 1, 1, 1, 1.42f, 1.42f, 1.42f, 1.42f};

                Deque<Float3> result = new ArrayDeque();

                PriorityQueue<pqEntry> pq = new PriorityQueue(1, pqComp);
                pq.add(new pqEntry(getHeuristic(startPos, targetPos), 0, startPos));

                int endi = Math.min(minCost.length - 1, toIndex(Math.max(target.x, start.x) + encradius, Math.max(target.z, start.z) + encradius));
                for (int i = Math.max(0, toIndex(Math.min(target.x, start.x) - encradius, Math.min(target.z, start.z) - encradius)); i <= endi; i++) {
                    minCost[i] = Float.MAX_VALUE;
                }
                minCost[startPos] = 0;

                int pos;
                float cost;

                boolean foundpath = true;

                initcounter += System.nanoTime() - time;
                time = System.nanoTime();

                outer:
                while (true) {

                    do {
                        if (pq.isEmpty()) {
                            result.add(target);
                            foundpath = false;
                            break outer;
                        }
                        pos = pq.peek().pos;
                        cost = pq.poll().realCost;
                    } while (cost > minCost[pos] || cost >= 1e6f);
                    if (pos == targetPos) {

                        break;
                    }

                    for (int i = 0; i < offset2.length; i++) {
                        if (pos % (smwidth) == 0 && offset2[i] % smwidth != 0) {
                            continue;
                        }
                        if ((pos + 1) % (smwidth) == 0 && offset2[i] % smwidth != 0) {
                            continue;
                        }
                        if (!inBounds(pos + offset2[i], minCost.length)) {
                            continue;
                        }
                        float ncost = offsetCostMod[i] * Math.max(getCost(slopeMap[pos + offset2[i]], maxSlope), getCost(slopeMap[pos + offset2check[i]], maxSlope));
                        if (cost + ncost < minCost[pos + offset2[i]]
                                && (getDistance(start, toFloat3(pos + offset2[i])) < 1.5f * encradius
                                || getDistance(target, toFloat3(pos + offset2[i])) < 1.5f * encradius)) {

                            //pathTo[pos + offset2[i]] = pos;
                            minCost[pos + offset2[i]] = cost + ncost;
                            pq.add(new pqEntry(getHeuristic(pos + offset2[i], targetPos) + minCost[pos + offset2[i]],
                                    minCost[pos + offset2[i]], pos + offset2[i]));
                            //command.mark(toFloat3(pos+offset[i]), "for " + (getHeuristic(pos + offset[i], targetPos) + minCost[pos + offset[i]]));
                        }
                    }
                }

                float totalcost = minCost[targetPos];
                if (foundpath) {
                    task.startarea.queueConnection(task.targetarea, totalcost, task.movementType);
                    task.targetarea.queueConnection(task.startarea, totalcost, task.movementType);
                    minCostPerElmo[offset] = Math.min(minCostPerElmo[offset], totalcost / getDistance(task.start, task.target));
                } else {
                    task.startarea.queueConnection(null, -1, task.movementType);
                    task.targetarea.queueConnection(null, -1, task.movementType);
                }
                loopcounter += System.nanoTime() - time;
            }

            command.debug("Thread " + offset + " finished queueing " + (initcounter / 1e9) + " - " + (loopcounter / 1e9), false);
            queuingDone.countDown();
            queuingDone.await();
            int sum = 0;
            for (int i = offset; i < command.areaManager.getAreas().size(); i += modulo) {
                command.areaManager.getAreas().get(i).applyConnections();
                sum++;
            }
            countdown.countDown();
        } catch (Exception ex) {
            command.debug("exception in pathfinder precalcthread ", ex);
        }
    }

    boolean inBounds(int num, int max) {
        return num < max && num >= 0;
    }

    public float getDistance(Float3 start, Float3 target) {
        return (float) Math.sqrt((start.x - target.x) * (start.x - target.x) + (start.z - target.z) * (start.z - target.z));
    }

    float getCost(float slope, float maxSlope) {
        if (slope > maxSlope) {
            return Float.MAX_VALUE;
        }
        return 10 * (slope / maxSlope + ((slope > maxSlope) ? (1e9f) : (0))) + 1;
    }

    Float3 toFloat3(int pos) {
        return new Float3(mapRes * (pos % (smwidth)), 0, mapRes * (pos / (smwidth)));
    }

    float getHeuristic(int start, int trg) {
        //return Math.abs(start % smwidth - trg % smwidth) + Math.abs(start / smwidth - trg / smwidth);//manhattan distance only works without diagonal paths
        return (float) Math.sqrt((start % smwidth - trg % smwidth) * (start % smwidth - trg % smwidth) + (start / smwidth - trg / smwidth) * (start / smwidth - trg / smwidth));
    }

    int toIndex(Float3 target) {
        return (int) (target.z / mapRes) * smwidth + (int) (target.x / mapRes);
    }

    int toIndex(float x, float z) {
        return (int) (z / mapRes) * smwidth + (int) (x / mapRes);
    }

    static class PrecalcTask {

        public final ZoneManager.Area startarea;
        public final Pathfinder.MovementType movementType;
        public final ZoneManager.Area targetarea;
        public final float maxSlope;
        public final Float3 start;
        public final Float3 target;
        public final float encradius;

        public PrecalcTask(ZoneManager.Area startarea, Pathfinder.MovementType movementType, ZoneManager.Area targetarea) {
            this.startarea = startarea;
            this.movementType = movementType;
            this.targetarea = targetarea;
            this.maxSlope = movementType.getMaxSlope();
            this.start = new Float3(startarea.getPos());
            this.target = new Float3(targetarea.getPos());
            this.encradius = startarea.getEnclosingRadius();
        }
    }

}

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package zkcbai.unitHandlers;

import com.springrts.ai.oo.AIFloat3;
import com.springrts.ai.oo.clb.OOAICallback;
import com.springrts.ai.oo.clb.Unit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import zkcbai.Command;
import zkcbai.UpdateListener;
import zkcbai.helpers.AreaChecker;
import zkcbai.helpers.Pathfinder;
import zkcbai.helpers.ZoneManager;
import zkcbai.helpers.ZoneManager.Area;
import zkcbai.helpers.ZoneManager.Zone;
import zkcbai.unitHandlers.betterSquads.AntiAirSquad;
import zkcbai.unitHandlers.units.AISquad;
import zkcbai.unitHandlers.units.AITroop;
import zkcbai.unitHandlers.units.AIUnit;
import zkcbai.unitHandlers.units.Enemy;
import zkcbai.unitHandlers.units.tasks.MoveTask;
import zkcbai.unitHandlers.units.tasks.Task;
import zkcbai.unitHandlers.units.tasks.WaitTask;

/**
 *
 * @author User
 */
public class CreepHandler extends UnitHandler implements UpdateListener {

    public CreepHandler(Command cmd, OOAICallback clbk) {
        super(cmd, clbk);
        cmd.addUpdateListener(this);
    }

    @Override
    public AIUnit addUnit(Unit u) {
        AIUnit au = new AIUnit(u, this);
        aiunits.put(u.getUnitId(), au);

        au.setAutoRepair(retreatForRepairs(au));
        troopIdle(au);
        return au;
    }

    @Override
    public void removeUnit(AIUnit u) {
        aiunits.remove(u.getUnit().getUnitId());
    }

    @Override
    public void troopIdle(AIUnit u) {
        u.wait(command.getCurrentFrame() + 10);

    }

    @Override
    public void troopIdle(AISquad s) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void abortedTask(Task t) {

    }

    @Override
    public void finishedTask(Task t) {

    }

    @Override
    public void reportSpam() {
        throw new AssertionError("Endless recursion");
    }

    @Override
    public void unitDestroyed(Enemy e, AIUnit killer) {

    }

    @Override
    public boolean retreatForRepairs(AITroop u) {
        return u.getDef().getHealth() > 1800 || u.getMetalCost() > 300 || true;
    }

    private int eval;

    @Override
    public void update(int frame) {
        if (frame % 50 == 4 && !aiunits.isEmpty()) {
            long time = System.currentTimeMillis();
            Map<ZoneManager.Area, Integer> unprotectedAreas = new HashMap<>();
            for (ZoneManager.Area a : command.areaManager.getAreas()) {
                if (a.getZone() != ZoneManager.Zone.own) {
                    continue;
                }
                if (!a.isFront() || !a.isReachable()) {
                    continue;
                }
                boolean unprotected = true;
//                for (AIUnit au : a.getNearbyUnits(rtRange)) {
//                    if (au.distanceTo(a.getPos()) < rtRange) {
//                        unprotected = false;
//                    }
//                }
                for (Area ar : a.getNeighbours(2)) {
                    if (unprotectedAreas.containsKey(ar)) {
                        unprotected = false;
                    }
                }
                if (unprotected) {
                    unprotectedAreas.put(a, 0);
                }
            }
            List<Area> hostile = new ArrayList();
            for (Area a : command.areaManager.getAreas()) {
                if (a.getZone() == ZoneManager.Zone.hostile) {
                    hostile.add(a);
                }
            }
            Random rnd = new Random();
            int smart = 0;
            for (final AIUnit creep : aiunits.values().toArray(new AIUnit[aiunits.values().size()])) {
                if (creep.needsRepairs()) {
                    creep.assignTask(new WaitTask(command.getCurrentFrame() + 200, this));
                    continue;
                }
                final float range = Math.max(creep.getMaxRange(), creep.getDef().getLosRadius());
                for (ZoneManager.Area a : unprotectedAreas.keySet()) {
                    if (a.distanceTo(creep.getPos()) < range) {
                        unprotectedAreas.put(a, unprotectedAreas.get(a) + 1);
                    }
                }
                if (System.currentTimeMillis() - time < 180 && command.getCommandDelay() < 30) {
                    smart++;

                    long time0 = System.nanoTime();
                    AIUnit nearestAlly = null;
                    float nearbyAllyStrength = 0;
                    for (AIUnit ally : command.getUnitsIn(creep.getPos(), 700)) {
                        if (ally.getDef().isAbleToAttack() && !AntiAirSquad.antiair.contains(ally.getDef())) {
                            nearbyAllyStrength += ally.getMetalCost();
                        }
                        if (ally.equals(creep)) {
                            continue;
                        }

                        if (ally.getNearestEnemy() != null
                                && ally.getNearestEnemy().distanceTo(ally.getPos()) < Math.min(ally.getNearestEnemy().getMaxRange(), 700)
                                && (nearestAlly == null || nearestAlly.distanceTo(creep.getPos()) > ally.distanceTo(creep.getPos()))) {
                            nearestAlly = ally;
                        }
                    }
                    if (nearestAlly != null) {
                        creep.fight(nearestAlly.getNearestEnemy().getPos(), command.getCurrentFrame() + 80);
                        command.debug(creep.getDef().getHumanName() + " helping out " + nearestAlly.getDef().getHumanName() + " against " + nearestAlly.getNearestEnemy().getDef().getHumanName());
                    }
                    long time1 = System.nanoTime();
                    /*
                    if (creep.getArea().getZone() != Zone.own) {
                        creep.moveTo(creep.getArea().getNearestArea(command.areaManager.FRIENDLY, creep.getMovementType()).getPos(),
                                command.getCurrentFrame() + 100);
                        continue;
                    }*/
                    Enemy closestEnemy = creep.getNearestEnemy();
                    //command.debug("Closest enemy: " + ((closestEnemy == null) ? (" nil") : closestEnemy.getDef().getHumanName()));
                    AIFloat3 pos = closestEnemy != null ? closestEnemy.getPos() : creep.getPos();
                    float danger = 3 * command.defenseManager.getDanger(pos) + command.defenseManager.getImmediateDanger(pos);

                    long time2 = System.nanoTime();
                    if (AntiAirSquad.antiair.contains(creep.getDef())) {
                        Enemy closestTarget = null;
                        for (Enemy e : command.getAvengerHandler().getEnemyAir()) {
                            if (!e.isTimedOut() && (closestTarget == null || e.distanceTo(creep.getPos()) < closestTarget.distanceTo(creep.getPos()) || (e.getArea().getZone() != Zone.hostile && closestTarget.getArea().getZone() == Zone.hostile))) {
                                closestTarget = e;
                            }
                        }
                        pos = closestTarget != null ? closestTarget.getPos() : creep.getPos();
                        danger = 3 * command.defenseManager.getDanger(pos) + command.defenseManager.getImmediateDanger(pos);
                        if (closestTarget != null
                                && (closestTarget.distanceTo(creep.getPos()) < 1500 && nearbyAllyStrength > danger
                                || command.areaManager.getArea(closestTarget.getPos()).getZone() != Zone.hostile)) {
                            if (closestTarget.distanceTo(creep.getPos()) < 700) {
                                creep.attack(closestTarget, command.getCurrentFrame() + 100);
                            } else {
                                creep.assignTask(new MoveTask(closestTarget.getPos(), command.getCurrentFrame() + 50, this, command.pathfinder.AVOID_GROUND_ENEMIES, command));
                            }
                            continue;
                        }
                    } else if (closestEnemy != null) {
                        Area closestFriendly = command.areaManager.getArea(closestEnemy.getPos()).getNearestArea(command.areaManager.FRIENDLY, creep.getMovementType());

                        if (closestEnemy.getDef().isAbleToFly() && closestEnemy.getMetalCost() > 200
                                && !closestEnemy.getDef().getName().equals("gunshipaa") && !AntiAirSquad.antiair.contains(creep.getDef())) {
                            danger += closestEnemy.getMetalCost() * 4;
                        }
                        if (danger * 1.05 < nearbyAllyStrength || creep.getHealth() > 4000 || closestEnemy.distanceTo(closestFriendly.getPos()) - closestFriendly.getEnclosingRadius() < Math.min(closestEnemy.getMaxRange(), 700)) {
                            if ((closestEnemy.getDef().getName().equalsIgnoreCase("corclog") || rnd.nextInt(6) < 1) && closestEnemy.getUnit() != null
                                    && creep.getMaxRange() < 400 && !creep.getDef().getName().equals("cormist")) {
                                //creep.attack(closestEnemy, command.getCurrentFrame() + 100);
                                creep.moveTo(closestEnemy.getPos(), command.getCurrentFrame() + 100);
                            } else {
                                AIFloat3 fpos = closestEnemy.getVel();
                                fpos.scale(50);
                                fpos.add(closestEnemy.getPos());
                                creep.fight(fpos, command.getCurrentFrame() + 100);
                            }

                            continue;
                        } else if (closestEnemy.distanceTo(creep.getPos()) < Math.min(closestEnemy.getMaxRange(), 700) * 1.1 + 250) {

                            creep.moveTo(creep.getArea().getNearestArea(command.areaManager.SAFE, creep.getMovementType()).getPos(),
                                    command.getCurrentFrame() + 100);
                            continue;

                        }
                    }

                    long time3 = System.nanoTime();

                    ZoneManager.Area best = null;
                    float bestscore = Float.NEGATIVE_INFINITY;
                    eval = 0;

                    for (ZoneManager.Area a : unprotectedAreas.keySet()) {
                        if (a.getCenterHeight() <= 0) {
                            continue;
                        }
                        float score = -creep.distanceTo(a.getPos()) / 600f;
                        final Area area = a;

                        for (Area a2 : unprotectedAreas.keySet()) {
                            eval++;
                            if ((a2.distanceTo(area.getPos()) < range)) {
                                score += 1f / Math.pow(unprotectedAreas.get(a2) + 1, 2);
                            }
                        }
                        if (score > bestscore) {
                            bestscore = score;
                            best = a;
                        }

                    }

                    if (best != null) {
                        creep.fight(best.getPos(), command.getCurrentFrame() + 100);
                        for (ZoneManager.Area a : unprotectedAreas.keySet()) {
                            if (a.distanceTo(best.getPos()) < range) {
                                unprotectedAreas.put(a, unprotectedAreas.get(a) + 1);
                            }
                        }
                    }

                    long time4 = System.nanoTime();

                    if (time4 - time0 > 1e6) {
                        command.debug("Ordering " + creep.getDef().getHumanName() + " took " + (time4 - time3) + "/" + (time3 - time2) + "/" + (time2 - time1) + "/" + (time1 - time0) + "ns. evaluated " + eval + " areas");
                    }

                } else if (creep.getNearestEnemy() != null) {

                    creep.fight(MoveTask.randomize(creep.getNearestEnemy().getPos(), 100), frame + 100);
                } else if (command.getRandomEnemy() != null) {
                    creep.fight(MoveTask.randomize(command.getRandomEnemy(creep.getUnit().getUnitId()).getPos(), 100), frame + 100);
                } else {
                    creep.fight(MoveTask.randomize(hostile.get(rnd.nextInt(hostile.size())).getPos(), 100), frame + 100);
                }
            }
            for (ZoneManager.Area a : unprotectedAreas.keySet()) {
                //a.addDebugString(unprotectedAreas.get(a).toString(), command.getCurrentFrame() + 50);
            }
            command.debug((smart * 100 / aiunits.size()) + "% smart creeps");
        }
    }
}

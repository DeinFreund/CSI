/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package zkcbai.unitHandlers;

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
        return u.getDef().getHealth() > 1800;
    }

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
                if (System.currentTimeMillis() - time < 180 && command.getCommandDelay() < 30) {
                    smart++;

                    AIUnit nearestAlly = null;
                    float nearbyAllyStrength = 0;
                    for (AIUnit ally : command.getUnitsIn(creep.getPos(), 1300)) {
                        if (ally.getDef().isAbleToAttack()) {
                            nearbyAllyStrength += ally.getMetalCost();
                        }
                        if (ally.equals(creep)) {
                            continue;
                        }

                        if (ally.getNearestEnemy() != null
                                && ally.getNearestEnemy().distanceTo(ally.getPos()) < ally.getNearestEnemy().getMaxRange()
                                && (nearestAlly == null || nearestAlly.distanceTo(creep.getPos()) > ally.distanceTo(creep.getPos()))) {
                            nearestAlly = ally;
                        }
                    }
                    if (nearestAlly != null) {
                        creep.fight(nearestAlly.getNearestEnemy().getPos(), command.getCurrentFrame() + 80);
                        command.debug(creep.getDef().getHumanName() + " helping out " + nearestAlly.getDef().getHumanName() + " against " + nearestAlly.getNearestEnemy().getDef().getHumanName());
                    }
                    /*
                    if (creep.getArea().getZone() != Zone.own) {
                        creep.moveTo(creep.getArea().getNearestArea(command.areaManager.FRIENDLY, creep.getMovementType()).getPos(),
                                command.getCurrentFrame() + 100);
                        continue;
                    }*/
                    Enemy closestEnemy = creep.getNearestEnemy();
                    float danger = command.defenseManager.getDanger(creep.getPos()) + command.defenseManager.getImmediateDanger(creep.getPos());

                    if (AntiAirSquad.antiair.contains(creep.getDef())) {
                        Enemy closestTarget = null;
                        for (Enemy e : command.getAvengerHandler().getEnemyAir()) {
                            if (!e.isTimedOut() && (closestTarget == null || e.distanceTo(creep.getPos()) < closestTarget.distanceTo(creep.getPos()))) {
                                closestTarget = e;
                            }
                        }
                        if (closestTarget != null
                                && (closestTarget.distanceTo(creep.getPos()) < 700 && nearbyAllyStrength > danger
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
                        if (danger * 2 < nearbyAllyStrength || closestEnemy.distanceTo(closestFriendly.getPos()) - closestFriendly.getEnclosingRadius() < closestEnemy.getMaxRange()) {
                            if ((closestEnemy.getDef().getName().equalsIgnoreCase("corclog") || closestEnemy.getDef().getName().equalsIgnoreCase("armsolar")
                                    || closestEnemy.getDef().getName().equalsIgnoreCase("corrazor") || rnd.nextInt(6) < 1) && closestEnemy.getUnit() != null
                                    && creep.getMaxRange() < 400 && !creep.getDef().getName().equals("cormist")) {
                                creep.attack(closestEnemy, command.getCurrentFrame() + 100);
                            } else {
                                creep.fight(closestEnemy.getPos(), command.getCurrentFrame() + 100);
                            }

                            continue;
                        } else if (closestEnemy.distanceTo(creep.getPos()) < closestEnemy.getMaxRange() * 1.1 + 250) {

                            creep.moveTo(creep.getArea().getNearestArea(command.areaManager.SAFE, creep.getMovementType()).getPos(),
                                    command.getCurrentFrame() + 100);
                            continue;

                        }
                    }

                    ZoneManager.Area best = null;
                    float bestscore = Float.NEGATIVE_INFINITY;

                    for (ZoneManager.Area a : unprotectedAreas.keySet()) {
                        if (a.getCenterHeight() <= 0) continue;
                        float score = -creep.distanceTo(a.getPos()) / 100f;
                        final Area area = a;

                        for (Area a2 : a.getConnectedAreas(Pathfinder.MovementType.spider, new AreaChecker() {
                            @Override
                            public boolean checkArea(ZoneManager.Area a) {
                                return (a.distanceTo(area.getPos()) < creep.getMaxRange()) && a.isFront() && a.isReachable() && a.getZone() == ZoneManager.Zone.own;
                            }
                        })) {
                            score += 1f / (unprotectedAreas.get(a2) + 1);
                        }
                        if (score > bestscore) {
                            bestscore = score;
                            best = a;
                        }

                    }
                    {
                        if (best != null) {
                            creep.fight(best.getPos(), command.getCurrentFrame() + 100);
                            for (ZoneManager.Area a : unprotectedAreas.keySet()) {
                                if (a.distanceTo(best.getPos()) < creep.getMaxRange()) {
                                    unprotectedAreas.put(a, unprotectedAreas.get(a) + 1);
                                }
                            }
                        }
                    }
                } else if (creep.getNearestEnemy() != null) {

                    creep.fight(MoveTask.randomize(creep.getNearestEnemy().getPos(), 100), frame + 100);
                } else if (command.getRandomEnemy() != null) {
                    creep.fight(MoveTask.randomize(command.getRandomEnemy(creep.getUnit().getUnitId()).getPos(), 100), frame + 100);
                } else {
                    creep.fight(MoveTask.randomize(hostile.get(rnd.nextInt(hostile.size())).getPos(), 100), frame + 100);
                }
            }
            command.debug((smart * 100 / aiunits.size()) + "% smart creeps");
        }
    }
}

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
import java.util.Set;
import zkcbai.Command;
import zkcbai.UpdateListener;
import zkcbai.helpers.AreaChecker;
import zkcbai.helpers.Pathfinder;
import zkcbai.helpers.ZoneManager;
import zkcbai.helpers.ZoneManager.Area;
import zkcbai.unitHandlers.units.AISquad;
import zkcbai.unitHandlers.units.AITroop;
import zkcbai.unitHandlers.units.AIUnit;
import zkcbai.unitHandlers.units.Enemy;
import zkcbai.unitHandlers.units.tasks.Task;

/**
 *
 * @author User
 */
public class SlasherHandler extends UnitHandler implements UpdateListener {

    public SlasherHandler(Command cmd, OOAICallback clbk) {
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
        return false;
    }

    @Override
    public void update(int frame) {
        if (frame % 50 == 4 && !aiunits.isEmpty()) {
            long time = System.currentTimeMillis();
            final float rtRange = clbk.getUnitDefByName("cormist").getMaxWeaponRange();
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
            for (AIUnit slasher : aiunits.values().toArray(new AIUnit[0])) {

                if (System.currentTimeMillis() - time < 180) {
                    smart ++;
                    Enemy closestEnemy = null;
                    Set<Enemy> enemies = command.getEnemyUnitsIn(slasher.getPos(), 800);
                    if (enemies.isEmpty()) {
                        enemies = command.getEnemyUnitsIn_Slow(slasher.getPos(), 1200);
                    }
                    for (Enemy e : enemies) {
                        if (closestEnemy == null || closestEnemy.distanceTo(slasher.getPos()) > e.distanceTo(slasher.getPos())) {
                            closestEnemy = e;
                        }
                    }
                    if (closestEnemy != null) {
                        if ((closestEnemy.getDef().getName().equalsIgnoreCase("corclog") || closestEnemy.getDef().getName().equalsIgnoreCase("armsolar")
                                || closestEnemy.getDef().getName().equalsIgnoreCase("corrazor") || rnd.nextInt(8) < 1) && closestEnemy.getUnit() != null) {
                            slasher.attack(closestEnemy.getUnit(), command.getCurrentFrame() + 100);
                        } else {
                            slasher.fight(closestEnemy.getPos(), command.getCurrentFrame() + 100);
                        }
                        continue;
                    }

                    ZoneManager.Area best = null;
                    float bestscore = Float.NEGATIVE_INFINITY;

                    for (ZoneManager.Area a : unprotectedAreas.keySet()) {
                        float score = -slasher.distanceTo(a.getPos()) / 100f;
                        final Area area = a;

                        for (Area a2 : a.getConnectedAreas(Pathfinder.MovementType.spider, new AreaChecker() {
                            @Override
                            public boolean checkArea(ZoneManager.Area a) {
                                return (a.distanceTo(area.getPos()) < rtRange) && a.isFront() && a.isReachable() && a.getZone() == ZoneManager.Zone.own;
                            }
                        })) {
                            score += 1f / (unprotectedAreas.get(a2) + 1);
                        }
                        for (ZoneManager.Area a2 : unprotectedAreas.keySet()) {
                            if (a2.distanceTo(a.getPos()) < rtRange) {
                            }
                        }
                        if (score > bestscore) {
                            bestscore = score;
                            best = a;
                        }

                    }
                    {
                        if (best != null) {
                            slasher.fight(best.getPos(), command.getCurrentFrame() + 100);
                            for (ZoneManager.Area a : unprotectedAreas.keySet()) {
                                if (a.distanceTo(best.getPos()) < rtRange) {
                                    unprotectedAreas.put(a, unprotectedAreas.get(a) + 1);
                                }
                            }
                        }
                    }
                } else {
                    slasher.fight(hostile.get(rnd.nextInt(hostile.size())).getPos(), frame + 100);
                }
            }
            command.debug( (smart* 100 / aiunits.size()) +  "% smart slashers");
        }
    }
}

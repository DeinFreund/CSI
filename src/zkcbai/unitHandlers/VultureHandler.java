/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package zkcbai.unitHandlers;

import com.springrts.ai.oo.clb.OOAICallback;
import com.springrts.ai.oo.clb.Unit;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import zkcbai.Command;
import zkcbai.UpdateListener;
import zkcbai.helpers.ZoneManager;
import zkcbai.helpers.ZoneManager.Area;
import zkcbai.helpers.ZoneManager.Zone;
import zkcbai.unitHandlers.units.AISquad;
import zkcbai.unitHandlers.units.AITroop;
import zkcbai.unitHandlers.units.AIUnit;
import zkcbai.unitHandlers.units.Enemy;
import zkcbai.unitHandlers.units.tasks.Task;
import zkcbai.unitHandlers.units.tasks.WaitTask;

/**
 *
 * @author User
 */
public class VultureHandler extends UnitHandler implements UpdateListener {

    Random rnd = new Random();

    public VultureHandler(Command cmd, OOAICallback clbk) {
        super(cmd, clbk);
        cmd.addUpdateListener(this);
    }

    @Override
    public AIUnit addUnit(Unit u) {
        AIUnit au = new AIUnit(u, this);
        aiunits.put(u.getUnitId(), au);

        au.setRetreat(AIUnit.RetreatState.Retreat65);
        au.setLandWhenIdle(false);
        au.setAutoRepair(false);
        troopIdle(au);
        return au;
    }

    @Override
    public void removeUnit(AIUnit u) {
        aiunits.remove(u.getUnit().getUnitId());
        repairing.remove(u);
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
    public void unitDestroyed(AIUnit a, Enemy e) {
        super.unitDestroyed(a, e);
        repairing.remove(a);
    }

    @Override
    public boolean retreatForRepairs(AITroop u) {
        return false;
    }

    private Set<AIUnit> repairing = new HashSet();

    @Override
    public void update(int frame) {
        for (AIUnit au : getUnits().toArray(new AIUnit[getUnits().size()])) {
            if (au.getUnit().getHealth() < 0.66 * au.getDef().getHealth()) {
                repairing.add(au);
                au.assignTask(new WaitTask(command.getCurrentFrame() + 100, this));
            }
        }
        for (AIUnit au : repairing.toArray(new AIUnit[repairing.size()])) {
            if (au.getUnit().getHealth() > 0.99 * au.getDef().getHealth()) {
                repairing.remove(au);
                command.mark(au.getPos(), "vulture repaired");
            }
        }
        if (frame % 50 == 4 && !aiunits.isEmpty()) {

            Map<ZoneManager.Area, Integer> unprotectedAreas = new HashMap<>();
            for (ZoneManager.Area a : command.areaManager.getAreas()) {
                if (a.distanceToFront() > 600 || a.getEnemyAADPS(false) > 40  && a.distanceToFront() < 300 || a.getZone() != Zone.own) {
                    continue;
                }
                boolean unprotected = true;
                for (Area ar : a.getNeighbours(3)) {
                    if (unprotectedAreas.containsKey(ar)) {
                        unprotected = false;
                    }
                }
                if (unprotected) {
                    unprotectedAreas.put(a, 0);
                }
            }
            for (final AIUnit creep : aiunits.values().toArray(new AIUnit[aiunits.values().size()])) {

                if (repairing.contains(creep)) {
                    continue;
                }
                ZoneManager.Area best = null;
                float bestscore = Float.NEGATIVE_INFINITY;

                final float range = 2200;
                for (ZoneManager.Area a : unprotectedAreas.keySet()) {
                    float score = -creep.distanceTo(a.getPos()) / 600f;
                    final Area area = a;

                    for (Area a2 : unprotectedAreas.keySet()) {
                        if ((a2.distanceTo(area.getPos()) < range)) {
                            score += 1f / Math.pow(unprotectedAreas.get(a2) + 1, 2);
                        }
                    }
                    if (score > bestscore) {
                        bestscore = score;
                        best = a;
                    }

                }
                {
                    if (best != null) {
                        creep.moveTo(best.getPos(), command.getCurrentFrame() + 100);
                        for (ZoneManager.Area a : unprotectedAreas.keySet()) {
                            if (a.distanceTo(best.getPos()) < range) {
                                unprotectedAreas.put(a, unprotectedAreas.get(a) + 1);
                            }
                        }
                    }
                }

            }
            for (final AIUnit creep : aiunits.values().toArray(new AIUnit[aiunits.values().size()])) {
                if (!repairing.contains(creep) && rnd.nextInt(120) == 0){
                    command.debug("Vulture scouting");
                    removeUnit(creep);
                    creep.setRetreat(AIUnit.RetreatState.RetreatOff);
                    creep.assignTask(new WaitTask(frame + 10000, this));
                    creep.moveTo(command.getRandomEnemy().getPos(), AIUnit.OPTION_NONE, frame + 10000);
                    creep.moveTo(command.getRandomEnemy().getPos(), AIUnit.OPTION_SHIFT_KEY, frame + 10000);
                    creep.moveTo(command.getRandomEnemy().getPos(), AIUnit.OPTION_SHIFT_KEY, frame + 10000);
                    creep.moveTo(command.getRandomEnemy().getPos(), AIUnit.OPTION_SHIFT_KEY, frame + 10000);
                    creep.moveTo(command.getRandomEnemy().getPos(), AIUnit.OPTION_SHIFT_KEY, frame + 10000);
                    creep.moveTo(command.getRandomEnemy().getPos(), AIUnit.OPTION_SHIFT_KEY, frame + 10000);
                    creep.moveTo(command.getRandomEnemy().getPos(), AIUnit.OPTION_SHIFT_KEY, frame + 10000);
                }
            }
            
        }
    }
}

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package zkcbai.unitHandlers;

import com.springrts.ai.oo.AIFloat3;
import com.springrts.ai.oo.clb.OOAICallback;
import com.springrts.ai.oo.clb.Unit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import zkcbai.Command;
import zkcbai.unitHandlers.units.AISquad;
import zkcbai.unitHandlers.units.AITroop;
import zkcbai.unitHandlers.units.AIUnit;
import zkcbai.unitHandlers.units.Enemy;
import zkcbai.unitHandlers.units.tasks.AttackGroundTask;
import zkcbai.unitHandlers.units.tasks.Task;
import zkcbai.unitHandlers.units.tasks.WaitTask;

/**
 *
 * @author User
 */
public class TurretHandler extends UnitHandler {

    Random rnd = new Random();

    public TurretHandler(Command cmd, OOAICallback clbk) {
        super(cmd, clbk);
    }

    @Override
    public AIUnit addUnit(Unit u) {
        aiunits.put(u.getUnitId(), new AIUnit(u, this));
        troopIdle(aiunits.get(u.getUnitId()));
        return aiunits.get(u.getUnitId());
    }

    @Override
    public void removeUnit(AIUnit u) {
        aiunits.remove(u.hashCode());
    }

    Map<AIUnit, AIFloat3> lastTarget = new HashMap();

    @Override
    public void troopIdle(AIUnit u) {
        if (!clbk.getEnemyUnitsIn(u.getPos(), u.getMaxRange() * 1.3f).isEmpty()) {
            u.assignTask(new WaitTask(command.getCurrentFrame() + 30, this));
            return;
        }
        AIFloat3 target = null;
        AIFloat3 unclippedtarget = null;
        float freq = 1f;
        if (u.getDef().getName().equals("corllt")) {
            freq = 10f;
        } else if (u.getDef().getName().equals("armdeva")) {
            freq = 0.3f;
        } else if (u.getDef().getName().equals("corhlt")) {
            freq = 0.3f;
        } else if (u.getDef().getName().equals("armartic")) {
            freq = 0.3f;
        } else {
            return;
        }
        boolean valid = false;
        List<Unit> nearbyFriendlies = clbk.getFriendlyUnitsIn(u.getPos(), u.getMaxRange() * 1.5f);
        int tries = 0;
        while (!valid) {
            if (!lastTarget.containsKey(u) || tries++ > 500) {
                float x = rnd.nextFloat() * u.getMaxRange() * 2 - u.getMaxRange();
                float z = rnd.nextFloat() * u.getMaxRange() * 2 - u.getMaxRange();
                target = new AIFloat3(x, 0, z);
                target.add(u.getPos());
            } else {
                float x = lastTarget.get(u).x;
                float z = lastTarget.get(u).z;
                float dist = (float) Math.sqrt((x - u.getPos().x) * (x - u.getPos().x) + (z - u.getPos().z) * (z - u.getPos().z));
                dist = Math.max(u.getMaxRange() * 0.7f, dist + (rnd.nextFloat() * u.getMaxRange() / 3f - u.getMaxRange() / 6f));
                //dist = 0.3f * u.getMaxRange();
                float arc = (float) Math.atan2(z - u.getPos().z, x - u.getPos().x);
                arc += tries * 0.1f / freq + rnd.nextFloat() / 18;
                target = new AIFloat3(dist * (float) Math.cos(arc), 0, dist * (float) Math.sin(arc));
                target.add(u.getPos());
            }
            target.y = clbk.getMap().getElevationAt(target.x, target.z);
            unclippedtarget = new AIFloat3(target);
            if (target.x > command.areaManager.getMapWidth()) {
                target.x = command.areaManager.getMapWidth();
            }
            if (target.x < 0) {
                target.x = 0;
            }
            if (target.z > command.areaManager.getMapHeight()) {
                target.z = command.areaManager.getMapHeight();
            }
            if (target.z < 0) {
                target.z = 0;
            }
            //target.y += u.distanceTo(target);
            valid = true;
            if (u.distanceTo3D(target) > u.getMaxRange()) {
                valid = false;
            }
            for (Unit unit : nearbyFriendlies) {
                if (Command.distance2D(target, unit.getPos()) < unit.getDef().getRadius()
                        + 1.2 * u.getDef().getWeaponMounts().get(0).getWeaponDef().getAreaOfEffect()) {
                    valid = false;
                }
            }
        }
        lastTarget.put(u, unclippedtarget);
        u.assignTask(new AttackGroundTask(target, command.getCurrentFrame() + (int) Math.ceil(10 / freq), this));
        /*
         AIFloat3 offset = new AIFloat3(u.getPos());
         offset.add(new AIFloat3(10,0,10));
         u.patrolTo(offset, -1);*/
    }

    @Override
    public void troopIdle(AISquad s) {
    }

    @Override
    public void abortedTask(Task t) {
        finishedTask(t);
    }

    @Override
    public void finishedTask(Task t) {
        if (t instanceof AttackGroundTask) {
            AttackGroundTask at = (AttackGroundTask) t;
            at.getLastExecutingUnit().getUnits().iterator().next().getUnit().stop((short) 0, command.getCurrentFrame() + 10);
        }
    }

    @Override
    public void reportSpam() {
    }

    @Override
    public void unitDestroyed(Enemy e, AIUnit killer) {
    }

    @Override
    public boolean retreatForRepairs(AITroop u) {
        return false;
    }

}

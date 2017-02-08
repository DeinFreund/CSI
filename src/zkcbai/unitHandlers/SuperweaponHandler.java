/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package zkcbai.unitHandlers;

import com.springrts.ai.oo.AIFloat3;
import com.springrts.ai.oo.clb.OOAICallback;
import com.springrts.ai.oo.clb.Unit;
import com.springrts.ai.oo.clb.UnitDef;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import zkcbai.Command;
import zkcbai.unitHandlers.units.AISquad;
import zkcbai.unitHandlers.units.AITroop;
import zkcbai.unitHandlers.units.AIUnit;
import zkcbai.unitHandlers.units.Enemy;
import zkcbai.unitHandlers.units.tasks.AttackGroundTask;
import zkcbai.unitHandlers.units.tasks.StopTask;
import zkcbai.unitHandlers.units.tasks.Task;

/**
 *
 * @author User
 */
public class SuperweaponHandler extends UnitHandler {

    Random rnd = new Random();
    public final UnitDef METEOR = command.getCallback().getUnitDefByName("zenith");
    public final UnitDef BERTHA = command.getCallback().getUnitDefByName("armbrtha");

    public SuperweaponHandler(Command cmd, OOAICallback clbk) {
        super(cmd, clbk);
    }

    @Override
    public AIUnit addUnit(Unit u) {
        aiunits.put(u.getUnitId(), new AIUnit(u, this));
        AIUnit au = aiunits.get(u.getUnitId());
        if (au.getDef().equals(METEOR)) {
            au.getUnit().setFireState(0, (short) 0, Integer.MAX_VALUE);
            au.getUnit().setOn(true, (short)0, Integer.MAX_VALUE);
        }
        troopIdle(au);
        return au;
    }

    @Override
    public void removeUnit(AIUnit u) {
        aiunits.remove(u.hashCode());
    }

    Map<AIUnit, AIFloat3> lastTarget = new HashMap();

    public Enemy getVIPTarget(AIUnit au) {
        try {
            TreeMap<Float, Enemy> map = new TreeMap();
            for (Enemy e : command.getEnemyUnits(false)) {
                if (e.distanceTo(au.getPos()) < au.getMaxRange() * 0.99 && ! e.isAbleToFly()) {
                    map.put((-e.getMetalCost() * (e.isBuilding() ? 1f : 0.3f)  - e.getArea().getNearbyEnemies().size() * 100) / ((e.distanceTo(au.getPos()) + 400) / (e.getMaxRange() + 400)) + (float)Math.random(), e);
                    if ((e.getMetalCost() > 29999 || ("cafus amgeo corsilo").contains(e.getDef().getName().toLowerCase()) && e.getMetalCost() > 800) && (e.hasBeenSeen() && rnd.nextInt(3) < 2 || rnd.nextBoolean())) return e;
                }
            }

            int rand = rnd.nextInt(Math.min(map.size(), 5));
            for (Map.Entry<Float, Enemy> e : map.entrySet()) {
                command.debug(e.getValue().getDef().getHumanName());
                if (rand-- == 0) {
                    command.debug("Selected superweapon target: " + e.getValue().getDef().getHumanName());
                    return e.getValue();
                }
            }
        } catch (Exception ex) {
            command.debug("Unable to find vip for superwep", ex);
        }
        return command.getRandomEnemy();
    }

    private AIFloat3 snap(AIFloat3 a){
        return new AIFloat3(a.x, clbk.getMap().getElevationAt(a.x, a.z) ,a.z);
    }
    
    @Override
    public void troopIdle(AIUnit u) {
        if (u.getDef().equals(METEOR)) {
            u.assignTask(new AttackGroundTask(snap(getVIPTarget(u).getPos()), command.getCurrentFrame() + 30, this).queue(new StopTask(command.getCurrentFrame() + 30 * (10 + rnd.nextInt(50)), this)));
        }
        if (u.getDef().equals(BERTHA)) {
            u.assignTask(new AttackGroundTask(snap(getVIPTarget(u).getPos()), command.getCurrentFrame() + 30 * 45, this));
        }
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
    }

    @Override
    public void reportSpam() {
    }

    @Override
    public void unitDestroyed(AIUnit u, Enemy e) {
        super.unitDestroyed(u, e);

    }

    @Override
    public void unitDestroyed(Enemy e, AIUnit killer) {
    }

    @Override
    public boolean retreatForRepairs(AITroop u) {
        return false;
    }

}

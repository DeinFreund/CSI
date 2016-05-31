/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package zkcbai.unitHandlers;

import com.springrts.ai.oo.clb.OOAICallback;
import com.springrts.ai.oo.clb.Unit;
import com.springrts.ai.oo.clb.UnitDef;
import zkcbai.Command;
import zkcbai.UpdateListener;
import zkcbai.unitHandlers.FactoryHandler.Factory;
import zkcbai.unitHandlers.units.AISquad;
import zkcbai.unitHandlers.units.AITroop;
import zkcbai.unitHandlers.units.AIUnit;
import zkcbai.unitHandlers.units.Enemy;
import zkcbai.unitHandlers.units.tasks.DropTask;
import zkcbai.unitHandlers.units.tasks.LoadUnitTask;
import zkcbai.unitHandlers.units.tasks.Task;

/**
 *
 * @author User
 */
public class DropHandler extends UnitHandler implements UpdateListener {

    private final UnitDef ROACH = clbk.getUnitDefByName("corroach");
    private final UnitDef VALK = clbk.getUnitDefByName("corvalk");

    public DropHandler(Command cmd, OOAICallback clbk) {
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
        if (u.getDef().equals(VALK)) {
            AIUnit roach = null;
            for (AIUnit au : command.getUnitsIn(u.getPos(), 1000)) {
                if (au.getDef().equals(ROACH)) {
                    roach = au;
                }
            }
            Enemy vip = null;
            for (Enemy e : command.getEnemyUnits(true)) {
                if (vip == null || e.getMetalCost() > vip.getMetalCost()) {
                    vip = e;
                }
            }
            if (roach != null && vip != null) {

                u.assignTask(new DropTask(vip, roach, this, command));
            } else {
                u.wait(command.getCurrentFrame() + 30);
            }

        } else {
            u.wait(command.getCurrentFrame() + 30);
        }
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

    private boolean buildingDrop = false;

    @Override
    public void update(int frame) {
        Factory gs = null;
        Factory shield = null;
        for (Factory f : command.getFactoryHandler().getFacs()) {
            if (f.unit.getDef().getName().equalsIgnoreCase("factorygunship")) {
                gs = f;
            }
            if (f.unit.getDef().getName().equalsIgnoreCase("factoryshield")) {
                shield = f;
            }
        }
        if (!buildingDrop && gs != null && shield != null) {
            shield.queueUnit(ROACH);
            gs.queueUnit(VALK);
            buildingDrop = true;
        }
    }

}

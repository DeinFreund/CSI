/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package zkcbai.unitHandlers.units;

import com.springrts.ai.oo.AIFloat3;
import com.springrts.ai.oo.clb.OOAICallback;
import com.springrts.ai.oo.clb.Unit;
import com.springrts.ai.oo.clb.UnitDef;
import com.springrts.ai.oo.clb.WeaponMount;
import zkcbai.Command;
import zkcbai.UpdateListener;

/**
 *
 * @author User
 */
public abstract class FakeEnemy extends Enemy implements UpdateListener {

    protected final UnitDef fakeUnitDef;
    protected final int createTime;
    protected int removeTime = Integer.MAX_VALUE;

    public FakeEnemy(UnitDef fakeUnitDef, AIFloat3 pos, Command cmd, OOAICallback clbk) {
        super(pos, cmd, clbk);
        if (fakeUnitDef == null) {
            throw new AssertionError("FakeEnemy UnitDef is null");
        }
        this.fakeUnitDef = fakeUnitDef;
        this.createTime = cmd.getCurrentFrame();
        if (fakeUnitDef.getName().equals("cormex")) {
            command.areaManager.getNearestMex(getPos()).setEnemyMex(this);
        }
        removeTime = command.getCurrentFrame() + 30 * 60 * (int) fakeUnitDef.getCost(command.metal) / 200;
        
        cmd.addSingleUpdateListener(this, removeTime + 10);
        identify();
        if (isBuilding) {
            removeTime += 30 * 60 * 5;
        }
    }

    @Override
    public void update(int frame) {
        if (!alive) {
            return;
        }
        super.update(frame);
        if (frame > removeTime && neverSeen) {
            destroyMyself();
        }
    }

    @Override
    public void identify() {
        unitDef = fakeUnitDef;
        super.identify();
    }

    @Override
    public void setUnit(Unit unit) {
        super.setUnit(unit);
    }

    @Override
    public void setUnitId(int unitId) {
        super.setUnitId(unitId);
    }

    public abstract boolean isUnit(Unit u);

}

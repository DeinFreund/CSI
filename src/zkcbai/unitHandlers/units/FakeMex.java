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
import zkcbai.Command;

/**
 *
 * @author User
 */
public class FakeMex extends FakeEnemy {

    public FakeMex(AIFloat3 pos, Command cmd, OOAICallback clbk) {
        super(getMexDef(cmd), pos, cmd, clbk);
    }

    private static UnitDef getMexDef(Command cmd) {
        UnitDef com = cmd.getCallback().getUnitDefByName("cormex");
        if (com != null) {
            return com;
        }
        throw new AssertionError("Mex UnitDef not found");
    }

    @Override
    public boolean isUnit(Unit u) {
        return u.getDef().equals(getDef()) && distanceTo(u.getPos()) < 80;
    }

}

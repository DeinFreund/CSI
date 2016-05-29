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
public class FakeSlasher extends FakeEnemy {

    public FakeSlasher(AIFloat3 pos, Command cmd, OOAICallback clbk) {
        super(getComDef(cmd), pos, cmd, clbk);
        cmd.debug("New fake slasher created at " + pos);
    }

    private static UnitDef getComDef(Command cmd) {
        return cmd.getCallback().getUnitDefByName("cormist");
    }

    @Override
    public boolean isUnit(Unit u) {
        return distanceTo(u.getPos()) < 500;
    }

    @Override
    public int timeSinceLastSeen() {
        return 0;
    }

}

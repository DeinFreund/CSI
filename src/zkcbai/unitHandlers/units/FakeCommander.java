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
public class FakeCommander extends FakeEnemy {

    public FakeCommander(AIFloat3 pos, Command cmd, OOAICallback clbk) {
        super(getComDef(cmd), pos, cmd, clbk);
    }

    private static UnitDef getComDef(Command cmd) {
        UnitDef com = cmd.getCallback().getUnitDefByName("armcom1");
        if (com != null) {
            return com;
        }
        com = cmd.getCallback().getUnitDefByName("armcom");
        if (com != null) {
            return com;
        }
        for (UnitDef ud : cmd.getCallback().getUnitDefs()) {
            if (ud.getName().contains("com")) {
                return ud;
            }
        }
        throw new AssertionError("Com UnitDef not found");
    }

    @Override
    public boolean isUnit(Unit u) {
        return u.getDef().getName().contains("com");
    }
    
    
    @Override
    public int timeSinceLastSeen() {
        return 0;
    }

}

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package zkcbai;

import com.springrts.ai.oo.AIFloat3;
import com.springrts.ai.oo.clb.OOAICallback;
import com.springrts.ai.oo.clb.Unit;
import com.springrts.ai.oo.clb.WeaponDef;

/**
 *
 * @author User
 */
public class Command implements AI {
    
    private final OOAICallback callback;
    private final int ownTeamId;
    
    public Command(int teamId, OOAICallback callback){
        this.callback = callback;
        ownTeamId = teamId;
        
    }

    @Override
    public int luaMessage(String inData) {
        return 0;
    }

    @Override
    public int unitGiven(Unit unit, int oldTeamId, int newTeamId) {
        return 0;
    }

    @Override
    public int unitDamaged(Unit unit, Unit attacker, float damage, AIFloat3 dir, WeaponDef weaponDef, boolean paralyzer) {
        return 0;
    }

    @Override
    public int enemyEnterLOS(Unit enemy) {
        return 0;
    }

    @Override
    public int enemyLeaveLOS(Unit enemy) {
        return 0;
    }

    @Override
    public int enemyEnterRadar(Unit enemy) {
        return 0;
    }

    @Override
    public int enemyLeaveRadar(Unit enemy) {
        return 0;
    }

    @Override
    public int enemyDamaged(Unit enemy, Unit attacker, float damage, AIFloat3 dir, WeaponDef weaponDef, boolean paralyzer) {
        return 0;
    }

    @Override
    public int unitDestroyed(Unit unit, Unit attacker) {
        return 0;
    }

    @Override
    public int init(int teamId, OOAICallback callback) {
        return 0;
    }

    @Override
    public int unitMoveFailed(Unit unit) {
        return 0;
    }

    @Override
    public int unitIdle(Unit unit) {
        return 0;
    }

    @Override
    public int update(int frame) {
        return 0;
    }

    @Override
    public int unitCreated(Unit unit, Unit builder) {
        return 0;
    }

    @Override
    public int unitFinished(Unit unit) {
        return 0;
    }
    
}

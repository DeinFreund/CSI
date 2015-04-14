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
public class ZKCBAI extends com.springrts.ai.oo.AbstractOOAI{

    AI ai;
    
    @Override
    public int init(int teamId, OOAICallback callback) {
        callback.getGame().sendTextMessage("ZKCBAI initializing...", 0);
        ai = new Command(teamId, callback);
        return 0;
    }
    
    @Override
    public int luaMessage(String inData) {
        ai.luaMessage(inData);
        return 0;
    }

    @Override
    public int unitGiven(Unit unit, int oldTeamId, int newTeamId) {
        ai.unitGiven(unit, oldTeamId, newTeamId);
        return 0;
    }

    @Override
    public int unitDamaged(Unit unit, Unit attacker, float damage, AIFloat3 dir, WeaponDef weaponDef, boolean paralyzer) {
        ai.unitDamaged(unit, attacker, damage, dir, weaponDef, paralyzer);
        return 0;
    }

    @Override
    public int enemyEnterLOS(Unit enemy) {
        ai.enemyEnterLOS(enemy);
        return 0;
    }

    @Override
    public int enemyLeaveLOS(Unit enemy) {
        ai.enemyLeaveLOS(enemy);
        return 0;
    }

    @Override
    public int enemyEnterRadar(Unit enemy) {
        ai.enemyEnterRadar(enemy);
        return 0;
    }

    @Override
    public int enemyLeaveRadar(Unit enemy) {
        ai.enemyLeaveRadar(enemy);
        return 0;
    }

    @Override
    public int enemyDamaged(Unit enemy, Unit attacker, float damage, AIFloat3 dir, WeaponDef weaponDef, boolean paralyzer) {
        ai.enemyDamaged(enemy, attacker, damage, dir, weaponDef, paralyzer);
        return 0;
    }

    @Override
    public int unitDestroyed(Unit unit, Unit attacker) {
        ai.unitDestroyed(unit, attacker);
        return 0;
    }

    @Override
    public int unitMoveFailed(Unit unit) {
        ai.unitMoveFailed(unit);
        return 0;
    }

    @Override
    public int unitIdle(Unit unit) {
        ai.unitIdle(unit);
        return 0;
    }

    @Override
    public int update(int frame) {
        ai.update(frame);
        return 0;
    }

    @Override
    public int unitCreated(Unit unit, Unit builder) {
        ai.unitCreated(unit, builder);
        return 0;
    }

    @Override
    public int unitFinished(Unit unit) {
        ai.unitFinished(unit);
        return 0;
    }

    
    
}

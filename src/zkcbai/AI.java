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
public interface AI{
    
    public int luaMessage(String inData);
    public int unitGiven(Unit unit, int oldTeamId, int newTeamId);
    public int unitDamaged(Unit unit, Unit attacker, float damage, AIFloat3 dir, WeaponDef weaponDef, boolean paralyzer);
    public int enemyEnterLOS(Unit enemy);
    public int enemyLeaveLOS(Unit enemy);
    public int enemyEnterRadar(Unit enemy);
    public int enemyLeaveRadar(Unit enemy);
    public int enemyDamaged(Unit enemy, Unit attacker, float damage, AIFloat3 dir, WeaponDef weaponDef, boolean paralyzer);
    public int unitDestroyed(Unit unit, Unit attacker);
    public int init(int teamId, OOAICallback callback);
    public int unitMoveFailed(Unit unit);
    public int unitIdle(Unit unit);
    public int update(int frame);
    public int unitCreated(Unit unit, Unit builder);
    public int unitFinished(Unit unit);
    public int unitCaptured(Unit unit, int oldTeam, int newTeam);
}

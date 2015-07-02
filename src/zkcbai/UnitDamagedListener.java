/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package zkcbai;

import zkcbai.unitHandlers.units.AIUnit;
import zkcbai.unitHandlers.units.Enemy;

/**
 *
 * @author User
 */
public interface UnitDamagedListener {
    
    public void unitDamaged(AIUnit u, Enemy killer, float damage);
    
    public void unitDamaged(Enemy e, AIUnit killer, float damage);
}

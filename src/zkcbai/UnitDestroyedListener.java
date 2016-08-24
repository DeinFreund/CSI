/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package zkcbai;

import com.springrts.ai.oo.clb.Unit;
import zkcbai.unitHandlers.units.AIUnit;
import zkcbai.unitHandlers.units.Enemy;

/**
 *
 * @author User
 */
public interface UnitDestroyedListener {
    
    public void unitDestroyed(AIUnit u, Enemy killer);
    
    public void unitDestroyed(Unit u, Enemy killer);
    
    public void unitDestroyed(Enemy e, AIUnit killer);
}

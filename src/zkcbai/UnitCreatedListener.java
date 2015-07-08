/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package zkcbai;

import com.springrts.ai.oo.clb.Unit;
import zkcbai.unitHandlers.units.AIUnit;

/**
 *
 * @author User
 */
public interface UnitCreatedListener {
    
    public void unitCreated(Unit u, AIUnit builder);
}

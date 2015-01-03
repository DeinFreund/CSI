/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package zkcbai.helpers;

import com.springrts.ai.oo.clb.OOAICallback;
import zkcbai.Command;
import zkcbai.unitHandlers.units.AIUnit;

/**
 *
 * @author User
 */
public class DefenseManager extends Helper{

    public DefenseManager(Command cmd, OOAICallback clbk) {
        super(cmd, clbk);
    }

    @Override
    public void unitFinished(AIUnit u) {
    }

    @Override
    public void update(int frame) {
    }
    
}

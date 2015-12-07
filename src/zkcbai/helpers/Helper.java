/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package zkcbai.helpers;

import com.springrts.ai.oo.clb.OOAICallback;
import zkcbai.Command;
import zkcbai.UnitFinishedListener;
import zkcbai.UpdateListener;

/**
 *
 * @author User
 */
public abstract class Helper implements UnitFinishedListener, UpdateListener{
    
    protected OOAICallback clbk;
    protected static Command command;
    
    public Helper(Command cmd, OOAICallback clbk){
        command = cmd;
        this.clbk = clbk;
        command.addUnitFinishedListener(this);
        command.addUpdateListener(this);
    }
    
    /**
     * Gets called after all helpers have been initialized
     */ 
    public void init(){
        
    }
    
    
}

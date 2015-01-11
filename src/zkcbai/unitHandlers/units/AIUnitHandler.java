/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package zkcbai.unitHandlers.units;

import zkcbai.Command;

/**
 *
 * @author User
 */
public interface AIUnitHandler {

    public Command getCommand();

    public void troopIdle(AITroop u);
}

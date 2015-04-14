/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package zkcbai.helpers;

import com.springrts.ai.oo.clb.OOAICallback;
import zkcbai.Command;
import zkcbai.EnemyDiscoveredListener;
import zkcbai.unitHandlers.units.AIUnit;
import zkcbai.unitHandlers.units.Enemy;

/**
 *
 * @author User
 */
public class PlaceholderEnemies extends Helper implements EnemyDiscoveredListener{

        
    public PlaceholderEnemies(Command cmd, OOAICallback clbk) {
        super(cmd, clbk);
        //Enemy com = new Enemy(null, cmd, clbk);
        //Enemy fac = new Enemy(null, cmd, clbk);
        //cmd.enemyDiscovered();
    }

    @Override
    public void unitFinished(AIUnit u) {
    }

    @Override
    public void update(int frame) {
    }

    @Override
    public void enemyDiscovered(Enemy e) {
    }
    
}

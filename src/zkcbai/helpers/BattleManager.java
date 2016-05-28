/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package zkcbai.helpers;

import com.springrts.ai.oo.clb.OOAICallback;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import zkcbai.Command;
import zkcbai.EnemyDiscoveredListener;
import zkcbai.UnitDestroyedListener;
import zkcbai.unitHandlers.units.AIUnit;
import zkcbai.unitHandlers.units.Enemy;

/**
 *
 * @author User
 */
public class BattleManager extends Helper implements EnemyDiscoveredListener, UnitDestroyedListener {

    Set<AIUnit> freeUnits = new HashSet();
    Set<Enemy> freeEnemies = new HashSet();
    Set<Battle> battles = new HashSet();
    Map<AIUnit, Battle> unitBattleFinder = new HashMap();
    Map<Enemy, Battle> enemyBattleFinder = new HashMap();
    
    public BattleManager(Command cmd, OOAICallback clbk){
        super(cmd, clbk);
        cmd.addEnemyDiscoveredListener(this);
        cmd.addUnitDestroyedListener(this);
    }
    
    
    @Override
    public void unitFinished(AIUnit u) {
        freeUnits.add(u);
    }

    @Override
    public void update(int frame) {
        if (frame % 13 == 7){
            for (AIUnit au : freeUnits.toArray(new AIUnit[0])){
                for (Enemy e : command.getEnemyUnitsIn(au.getPos(), 600)){
                    if (au.distanceTo(e.getPos()) < Math.max(au.getMaxRange(), e.getMaxRange())){
                        freeUnits.remove(au);
                        if (enemyBattleFinder.containsKey(e)){
                            enemyBattleFinder.get(e).addUnit(au);
                        }else if (unitBattleFinder.containsKey(au)){
                            unitBattleFinder.get(au).addUnit(e);
                        }else{
                            Battle b = new Battle();
                            battles.add(b);
                            b.addUnit(au);
                            b.addUnit(e);
                        }
                    }
                }
            }
        }
    }

    @Override
    public void enemyDiscovered(Enemy e) {
        freeEnemies.add(e);
    }

    @Override
    public void unitDestroyed(AIUnit u, Enemy killer) {
        freeUnits.remove(u);
        if (unitBattleFinder.containsKey(u)){
            unitBattleFinder.get(u).removeUnit(u);
        }
    }

    @Override
    public void unitDestroyed(Enemy e, AIUnit killer) {
        freeEnemies.remove(e);
        if (enemyBattleFinder.containsKey(e)){
            enemyBattleFinder.get(e).removeUnit(e);
        }
    }


    public class Battle{
        
        Set<AIUnit> ownUnits = new HashSet();
        Set<Enemy> enemies = new HashSet();
        
        public Battle(){
            
        }
        
        public Set<AIUnit> getAIUnits(){
            return ownUnits;
        }
        
        public Set<Enemy> getEnemies(){
            return enemies;
        }
        
        public void removeUnit(AIUnit au){
            ownUnits.remove(au);
            unitBattleFinder.remove(au);
            if (enemies.isEmpty() && ownUnits.isEmpty()) battles.remove(this);
        }
        public void removeUnit(Enemy e){
            enemies.remove(e);
            enemyBattleFinder.remove(e);
            if (enemies.isEmpty() && ownUnits.isEmpty()) battles.remove(this);
        }
        
        public void addUnit(AIUnit au){
            ownUnits.add(au);
            unitBattleFinder.put(au, this);
            
        }
        public void addUnit(Enemy e){
            enemies.add(e);
            enemyBattleFinder.put(e, this);
            
        }
    }
}

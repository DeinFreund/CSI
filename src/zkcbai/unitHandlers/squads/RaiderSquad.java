/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package zkcbai.unitHandlers.squads;

import com.springrts.ai.oo.AIFloat3;
import com.springrts.ai.oo.clb.Group;
import com.springrts.ai.oo.clb.OOAICallback;
import zkcbai.Command;
import zkcbai.unitHandlers.FighterHandler;
import zkcbai.unitHandlers.units.AIUnit;
import zkcbai.unitHandlers.units.Enemy;

/**
 *
 * @author User
 */
public class RaiderSquad extends Squad {

    private static final int maxDist = 150;

    private AIFloat3 target;
    private Group group;

    public RaiderSquad(FighterHandler fighterHandler, Command command, OOAICallback callback, AIFloat3 target) {
        super(fighterHandler, command, callback);
        this.target = target;
        group = clbk.getGroups().get(0);
    }
    
    @Override
    public void addUnit(AIUnit u){
        super.addUnit(u);
        u.addToGroup(group);
    }

    private AIUnit getUnit() {
        for (AIUnit au : units) {
            return au;
        }
        return null;
    }
   

    @Override
    public void unitIdle(AIUnit u) {
        
        //command.mark(u.getPos(),"unit idle");
        if (!command.defenseManager.isRaiderAccessible(u.getPos())) {
            //command.mark(u.getPos(), "dangerous position for raider");
            u.moveTo(command.areaManager.getArea(u.getPos()).getNearestArea(command.areaManager.RAIDER_ACCESSIBLE).getPos(),
                    command.getCurrentFrame() + 5);
            u.moveTo(target,AIUnit.OPTION_SHIFT_KEY,Integer.MAX_VALUE);
            return;
        }
        AIFloat3 fpos = getPos();
        if (u.distanceTo(fpos) > maxDist) {
            for (AIUnit au : units) {
                    command.mark(au.getPos(), "gather");
                au.moveTo(fpos, command.getCurrentFrame() + 30);
                au.getUnit().wait(AIUnit.OPTION_SHIFT_KEY,Integer.MAX_VALUE);
                
            }
            return;
        }
        for (Enemy e : command.getEnemyUnitsIn(u.getPos(), 350)) {
            if (!e.getDef().isAbleToAttack() && command.defenseManager.isRaiderAccessible(e.getPos())) {
                for (AIUnit au : units) {
                    command.mark(au.getPos(), "fight");
                    au.fight(e.getPos(), command.getCurrentFrame() + 30);
                    au.getUnit().wait(AIUnit.OPTION_SHIFT_KEY,Integer.MAX_VALUE);
                }
                return;
            }
        }
        for (Enemy e : command.getEnemyUnitsIn(u.getPos(), 500)) {
            if ( command.defenseManager.isRaiderAccessible(e.getPos())) {
                for (AIUnit au : units) {
                    command.mark(au.getPos(), "fight2");
                    au.fight(e.getPos(), command.getCurrentFrame() + 30);
                    au.getUnit().wait(AIUnit.OPTION_SHIFT_KEY,Integer.MAX_VALUE);
                }
                return;
            }
        }
        if (distanceTo(target) < 100){
            
            return;
        }
        u.moveTo(target ,AIUnit.OPTION_NONE,command.getCurrentFrame() + 20);

    }

}

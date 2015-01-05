/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package zkcbai.unitHandlers.squads;

import com.springrts.ai.oo.AIFloat3;
import com.springrts.ai.oo.clb.Group;
import com.springrts.ai.oo.clb.OOAICallback;
import java.util.Collection;
import zkcbai.Command;
import zkcbai.unitHandlers.FighterHandler;
import zkcbai.unitHandlers.units.AIUnit;
import zkcbai.unitHandlers.units.Enemy;
import zkcbai.unitHandlers.units.tasks.FightTask;
import zkcbai.unitHandlers.units.tasks.Task;
import zkcbai.unitHandlers.units.tasks.TaskIssuer;

/**
 *
 * @author User
 */
public class RaiderSquad extends Squad implements TaskIssuer {

    private static final int maxDist = 150;
    private static final int maxCombatDist = 350;

    private AIFloat3 target;
    private Group group;

    public RaiderSquad(FighterHandler fighterHandler, Command command, OOAICallback callback, AIFloat3 target) {
        super(fighterHandler, command, callback);
        this.target = target;
        group = clbk.getGroups().get(0);
    }

    @Override
    public void addUnit(AIUnit u) {
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
        Collection<Enemy> enemies = command.getEnemyUnitsIn(u.getPos(), 350);
        
        if (!command.defenseManager.isRaiderAccessible(u.getPos())) {
            //command.mark(u.getPos(), "dangerous position for raider");
            u.moveTo(command.areaManager.getArea(u.getPos()).getNearestArea(command.areaManager.RAIDER_ACCESSIBLE).getPos(),
                    command.getCurrentFrame() + 12);
            u.moveTo(target, AIUnit.OPTION_SHIFT_KEY, Integer.MAX_VALUE);
            return;
        }
        AIFloat3 fpos = getPos();
        if ((u.distanceTo(fpos) > maxDist && enemies.isEmpty())||(u.distanceTo(fpos) > maxCombatDist)) {
            for (AIUnit au : units) {
                //command.mark(au.getPos(), "gather");
                au.moveTo(fpos, command.getCurrentFrame() + 30);
                au.getUnit().wait(AIUnit.OPTION_SHIFT_KEY, Integer.MAX_VALUE);

            }
            return;
        }
        for (Enemy e : enemies) {
            if (!e.getDef().isAbleToAttack() && command.defenseManager.isRaiderAccessible(e.getPos())) {
                for (AIUnit au : units) {
                    //command.mark(au.getPos(), "fight");
                    au.assignTask(new FightTask(e.getPos(), command.getCurrentFrame() + 30,this));
//                    au.fight(e.getPos(), command.getCurrentFrame() + 30);
//                    au.getUnit().wait(AIUnit.OPTION_SHIFT_KEY, Integer.MAX_VALUE);
                }
                return;
            }
        }
        for (Enemy e : command.getEnemyUnitsIn(u.getPos(), 500)) {
            if (command.defenseManager.isRaiderAccessible(e.getPos())) {
                for (AIUnit au : units) {
                    
                    //command.mark(au.getPos(), "fight2");
                    au.assignTask(new FightTask(e.getPos(), command.getCurrentFrame() + 30,this));
//                    au.fight(e.getPos(), command.getCurrentFrame() + 30);
//                    au.getUnit().wait(AIUnit.OPTION_SHIFT_KEY, Integer.MAX_VALUE);
                }
                return;
            }
        }
        if (distanceTo(target) < 100) {

            //command.mark(target, "fight3");
//            u.fight(target, command.getCurrentFrame() + 30);
//            u.getUnit().wait(AIUnit.OPTION_SHIFT_KEY, Integer.MAX_VALUE);
            u.assignTask(new FightTask(target, command.getCurrentFrame() + 30,this));
            return;
        }
        u.moveTo(target, AIUnit.OPTION_NONE, command.getCurrentFrame() + 20);

    }

    @Override
    public void abortedTask(Task t) {
        
    }

    @Override
    public void finishedTask(Task t) {
        
    }

}

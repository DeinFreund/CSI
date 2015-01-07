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
import zkcbai.helpers.AreaChecker;
import zkcbai.helpers.ZoneManager;
import zkcbai.unitHandlers.FighterHandler;
import zkcbai.unitHandlers.units.AIUnit;
import zkcbai.unitHandlers.units.Enemy;
import zkcbai.unitHandlers.units.tasks.AttackTask;
import zkcbai.unitHandlers.units.tasks.FightTask;
import zkcbai.unitHandlers.units.tasks.MoveTask;
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
    public void unitIdle(final AIUnit u) {

        AreaChecker RAIDER_ACCESSIBLE_DISTANT = new AreaChecker() {
            @Override
            public boolean checkArea(ZoneManager.Area a) {
                return command.defenseManager.isRaiderAccessible(a.getPos()) && u.distanceTo(a.getPos()) > 250;
            }
        };

        if (target == null) {
            target = fighterHandler.requestNewTarget(this);
        }

        //command.mark(u.getPos(),"unit idle");
        Collection<Enemy> enemies = command.getEnemyUnitsIn(u.getPos(), 650);

        if (!command.defenseManager.isRaiderAccessible(u.getPos())) {
            //command.mark(u.getPos(), "dangerous position for raider");
            if (u.distanceTo(target) > 300) {
                u.assignTask(new MoveTask(target, command.getCurrentFrame() + 20, this, command.pathfinder.RAIDER_PATH, command));
            } else {
                u.assignTask(new MoveTask(command.areaManager.getArea(target).getNearestArea(RAIDER_ACCESSIBLE_DISTANT).getPos(),
                        command.getCurrentFrame() + 20, this, command.pathfinder.RAIDER_PATH, command));
            }
//            u.moveTo(command.areaManager.getArea(u.getPos()).getNearestArea(command.areaManager.RAIDER_ACCESSIBLE).getPos(),
//                    command.getCurrentFrame() + 12);
//            u.moveTo(target, AIUnit.OPTION_SHIFT_KEY, Integer.MAX_VALUE);
            return;
        }
        AIFloat3 fpos = getPos();
        if (((u.distanceTo(fpos) > maxDist && enemies.isEmpty()) || (u.distanceTo(fpos) > maxCombatDist))
                && command.defenseManager.isRaiderAccessible(fpos) && command.pathfinder.findPath(u.getPos(), fpos,
                        u.getUnit().getDef().getMoveData().getMaxSlope(), command.pathfinder.RAIDER_PATH).size() > 1) {
            for (AIUnit au : units) {
                //command.mark(au.getPos(), "gather");
                au.assignTask(new MoveTask(fpos, command.getCurrentFrame() + 40, this, command.pathfinder.RAIDER_PATH, command));
                au.getUnit().wait(AIUnit.OPTION_SHIFT_KEY, Integer.MAX_VALUE);

            }
            return;
        }
        for (Enemy e : enemies) {
            if (e.isBuilding() && e.getDef().isAbleToAttack() && command.defenseManager.isRaiderAccessible(e.getPos())) {
                for (AIUnit au : units) {
                    //command.mark(au.getPos(), "fight");
                    au.assignTask(new AttackTask(e, command.getCurrentFrame() + 40, this, command));
//                    au.fight(e.getPos(), command.getCurrentFrame() + 30);
//                    au.getUnit().wait(AIUnit.OPTION_SHIFT_KEY, Integer.MAX_VALUE);
                }
                return;
            }
        }
        for (Enemy e : enemies) {
            if (!e.getDef().isAbleToAttack() && command.defenseManager.isRaiderAccessible(e.getPos())) {
                for (AIUnit au : units) {
                    //command.mark(au.getPos(), "fight");
                    au.assignTask(new FightTask(e.getPos(), command.getCurrentFrame() + 30, this));
//                    au.fight(e.getPos(), command.getCurrentFrame() + 30);
//                    au.getUnit().wait(AIUnit.OPTION_SHIFT_KEY, Integer.MAX_VALUE);
                }
                return;
            }
        }
        Collection<Enemy> farens = command.getEnemyUnitsIn(u.getPos(), 900);
        for (Enemy e : farens) {
            if (command.defenseManager.isRaiderAccessible(e.getPos())) {
                for (AIUnit au : units) {

                    //command.mark(au.getPos(), "fight2");
                    au.assignTask(new FightTask(e.getPos(), command.getCurrentFrame() + 30, this));
//                    au.fight(e.getPos(), command.getCurrentFrame() + 30);
//                    au.getUnit().wait(AIUnit.OPTION_SHIFT_KEY, Integer.MAX_VALUE);
                }
                return;
            }
        }
        if (u.distanceTo(target) < 100 //checks if target is unreachable
                || command.pathfinder.findPath(u.getPos(), target, u.getUnit().getDef().getMoveData().getMaxSlope(),
                        command.pathfinder.RAIDER_PATH).size() <= 1) {

            //command.mark(target, "fight3");
//            u.fight(target, command.getCurrentFrame() + 30);
//            u.getUnit().wait(AIUnit.OPTION_SHIFT_KEY, Integer.MAX_VALUE);
            if (farens.isEmpty()) {
                target = fighterHandler.requestNewTarget(this);
                command.mark(target, "new target");
                u.assignTask(new FightTask(target, command.getCurrentFrame() + 20, this));
            } else {
                u.assignTask(new FightTask(target, command.getCurrentFrame() + 60, this));
            }
            return;
        }
        u.assignTask(new MoveTask(target, command.getCurrentFrame() + 40, this, command.pathfinder.RAIDER_PATH, command));

    }

    @Override
    public void abortedTask(Task t) {

    }

    @Override
    public void finishedTask(Task t) {

    }

    @Override
    public void reportSpam() {
        throw new RuntimeException("I spammed MoveTasks!");
    }

}

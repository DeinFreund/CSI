/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package zkcbai.unitHandlers.squads;

import com.springrts.ai.oo.AIFloat3;
import com.springrts.ai.oo.clb.Group;
import com.springrts.ai.oo.clb.OOAICallback;
import com.springrts.ai.oo.clb.UnitDef;
import java.util.Collection;
import java.util.HashSet;
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
    private Collection<UnitDef> cheapDefenseTowers;
    private Collection<UnitDef> unimportantUnits;

    public RaiderSquad(FighterHandler fighterHandler, Command command, OOAICallback callback, AIFloat3 target) {
        super(fighterHandler, command, callback);
        this.target = target;

        this.cheapDefenseTowers = new HashSet();
        this.unimportantUnits = new HashSet();
        group = clbk.getGroups().get(0);

        String[] cheapDefenseTowers = new String[]{"corrl", "corllt"};
        String[] unimportantUnits = new String[]{"armsolar"};

        for (String s : cheapDefenseTowers) {
            this.cheapDefenseTowers.add(clbk.getUnitDefByName(s));
        }
        for (String s : unimportantUnits) {
            this.unimportantUnits.add(clbk.getUnitDefByName(s));
        }
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

    private boolean isWinPossible(AIFloat3 pos) {
        return true; // TODO find a better way to determine chances
        //return getMetalCost() > command.defenseManager.getImmediateDanger(pos);
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

        AIFloat3 target = command.areaManager.getArea(this.target).getNearestArea(command.areaManager.RAIDER_ACCESSIBLE).getPos();
        //tactical field hiding

        Collection<Enemy> enemies = command.getEnemyUnitsIn(u.getPos(), 650);

        //AVOID RIOTS
        if (!command.defenseManager.isRaiderAccessible(u.getPos())) {
            u.assignTask(new MoveTask(command.areaManager.getArea(target).getNearestArea(RAIDER_ACCESSIBLE_DISTANT).getPos(),
                    command.getCurrentFrame() + 20, this, command.pathfinder.RAIDER_PATH, command));

            return;
        }

        //GATHER
        AIFloat3 fpos = getPos();
        if (((u.distanceTo(fpos) > maxDist && enemies.isEmpty()) || (u.distanceTo(fpos) > maxCombatDist)) && isWinPossible(fpos)
                && command.defenseManager.isRaiderAccessible(fpos) && command.pathfinder.findPath(u.getPos(), fpos,
                        u.getUnit().getDef().getMoveData().getMaxSlope(), command.pathfinder.RAIDER_PATH).size() > 1) {
            for (AIUnit au : units) {
                //command.mark(au.getPos(), "gather");
                au.assignTask(new MoveTask(fpos, command.getCurrentFrame() + 40, this, command.pathfinder.RAIDER_PATH, command));
                au.getUnit().wait(AIUnit.OPTION_SHIFT_KEY, Integer.MAX_VALUE);

            }
            return;
        }

        Enemy best = null;
        float minDist, minHealth;

        //KILL UNDEFENDED BUILDINGS
        minHealth = Float.MAX_VALUE;
        for (Enemy e : enemies) {
            if (command.defenseManager.getImmediateDanger(e.getPos()) == 0 && !unimportantUnits.contains(e.getDef())
                    && (e.getUnit().getHealth() > 0 ? e.getUnit().getHealth() : 1000) < minHealth && isWinPossible(e.getPos())) {
                minHealth = e.getUnit().getHealth() > 0 ? e.getUnit().getHealth() : 1000;
                best = e;
            }
        }
        if (best != null) {
            for (AIUnit au : units) {
                au.assignTask(new AttackTask(best, command.getCurrentFrame() + 40, this, command));
            }
            return;
        }

        //KILL BASIC DEFENSE
        minDist = Float.MAX_VALUE;
        for (Enemy e : enemies) {
            if (cheapDefenseTowers.contains(e.getDef()) && command.defenseManager.isRaiderAccessible(e.getPos())
                    && e.distanceTo(fpos) < minDist && isWinPossible(e.getPos())) {
                minDist = e.distanceTo(fpos);
                best = e;
            }
        }
        if (best != null) {
            for (AIUnit au : units) {
                au.assignTask(new AttackTask(best, command.getCurrentFrame() + 40, this, command));
            }
            return;

        }

        //KILL FIGHTERS
        minHealth = Float.MAX_VALUE;
        for (Enemy e : enemies) {
            if (e.getDef().isAbleToAttack() && (e.getUnit().getHealth() > 0 ? e.getUnit().getHealth() : 1000) < minHealth
                    && command.defenseManager.isRaiderAccessible(e.getPos()) && isWinPossible(e.getPos())) {
                minHealth = e.getUnit().getHealth() > 0 ? e.getUnit().getHealth() : 1000;
                best = e;
            }
        }
        if (best != null) {
            for (AIUnit au : units) {
                au.assignTask(new AttackTask(best, command.getCurrentFrame() + 40, this, command));
            }
            return;
        }

        //KILL OTHER ENEMIES
        for (Enemy e : enemies) {
            if (command.defenseManager.isRaiderAccessible(e.getPos()) && isWinPossible(e.getPos())) {
                for (AIUnit au : units) {
                    au.assignTask(new FightTask(e.getPos(), command.getCurrentFrame() + 35, this));
                }
                return;
            }
        }

        //CHECK IF REINFORCEMENTS NEEDED
        if (distanceTo(target) < 500) {
            for (Enemy e : enemies) {
                if (command.defenseManager.isRaiderAccessible(e.getPos())) {
                    //reinforcements needed
                    u.moveTo(command.getStartPos(), command.getCurrentFrame() + 25);
                    fighterHandler.requestReinforcements(this);
                    return;
                }
            }
        }

        if (u.distanceTo(target) < 100 //checks if target is unreachable
                || command.pathfinder.findPath(u.getPos(), target, u.getUnit().getDef().getMoveData().getMaxSlope(),
                        command.pathfinder.RAIDER_PATH).size() <= 1) {

            target = fighterHandler.requestNewTarget(this);
            command.mark(target, "new target");
            u.assignTask(new FightTask(target, command.getCurrentFrame() + 20, this));
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

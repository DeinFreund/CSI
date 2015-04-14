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
import zkcbai.unitHandlers.units.AISquad;
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
public class GenericSquad extends SquadHandler implements TaskIssuer {

    private static final int maxDist = 200;
    private static final int maxCombatDist = 400;
    private static final int maxWaitTime = 300;

    private AIFloat3 target;
    private AISquad squad;
    private Collection<UnitDef> cheapDefenseTowers;
    private Collection<UnitDef> unimportantUnits;
    private int notAccessibleSince = -1;

    public GenericSquad(FighterHandler fighterHandler, Command command, OOAICallback callback, AIFloat3 target) {
        super(fighterHandler, command, callback);
        this.target = target;

        this.cheapDefenseTowers = new HashSet();
        this.unimportantUnits = new HashSet();
        squad = new AISquad(this);

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
        squad.addUnit(u);
    }
    
    @Override
    public void removeUnit(AIUnit u){
        super.removeUnit(u);
        squad.removeUnit(u, fighterHandler);
    }

    @Override
    public AIFloat3 getPos(){
        return squad.getPos();
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
    public void troopIdle(final AISquad u) {

        AreaChecker RAIDER_ACCESSIBLE_DISTANT = new AreaChecker() {
            @Override
            public boolean checkArea(ZoneManager.Area a) {
                return command.defenseManager.isRaiderAccessible(a.getPos()) && u.distanceTo(a.getPos()) > 500;
            }
        };

        if (target == null ||(notAccessibleSince > 0 && command.getCurrentFrame() - notAccessibleSince > maxWaitTime)) {
            target = fighterHandler.requestNewTarget(this);
        }
    
        if (notAccessibleSince < 0 && !command.defenseManager.isRaiderAccessible(this.target)){
            notAccessibleSince = command.getCurrentFrame();
        }
        if (notAccessibleSince > 0 && command.defenseManager.isRaiderAccessible(this.target)){
            notAccessibleSince = -1;
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
        if (((squad.getRadius() > maxDist && enemies.isEmpty()) || (squad.getRadius() > maxCombatDist)) && isWinPossible(fpos)
                && command.defenseManager.isRaiderAccessible(fpos) && command.pathfinder.findPath(u.getPos(), fpos,
                        u.getMaxSlope(), command.pathfinder.RAIDER_PATH).size() > 1) {
                u.assignTask(new MoveTask(fpos, command.getCurrentFrame() + 40, this, command.pathfinder.RAIDER_PATH, command));
                

            
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
                u.assignTask(new AttackTask(best, command.getCurrentFrame() + 40, this, command));
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
                u.assignTask(new AttackTask(best, command.getCurrentFrame() + 40, this, command));
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
                u.assignTask(new AttackTask(best, command.getCurrentFrame() + 40, this, command));
            return;
        }

        //KILL OTHER ENEMIES
        for (Enemy e : enemies) {
            if (command.defenseManager.isRaiderAccessible(e.getPos()) && isWinPossible(e.getPos())) {
                    u.assignTask(new FightTask(e.getPos(), command.getCurrentFrame() + 35, this));
                
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
                || command.pathfinder.findPath(u.getPos(), target, u.getMaxSlope(),
                        command.pathfinder.RAIDER_PATH).size() <= 1) {

            this.target = fighterHandler.requestNewTarget(this);
            //command.mark(target, "new target");
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

    @Override
    public void troopIdle(AIUnit u) {
        throw new RuntimeException("Wrong troopIdle called on RaiderSquad");
    }


    @Override
    public AIUnit.UnitType getType() {
        return AIUnit.UnitType.raider;
    }

}

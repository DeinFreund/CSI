/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package zkcbai.unitHandlers.squads;

import com.springrts.ai.oo.AIFloat3;
import com.springrts.ai.oo.clb.OOAICallback;
import zkcbai.Command;
import zkcbai.unitHandlers.FighterHandler;
import zkcbai.unitHandlers.units.AISquad;
import zkcbai.unitHandlers.units.AIUnit;
import zkcbai.unitHandlers.units.tasks.FightTask;
import zkcbai.unitHandlers.units.tasks.MoveTask;
import zkcbai.unitHandlers.units.tasks.Task;
import zkcbai.unitHandlers.units.tasks.TaskIssuer;

/**
 *
 * @author User
 */
public class ScoutSquad extends SquadHandler implements TaskIssuer {

    public ScoutSquad(FighterHandler fighterHandler, Command command, OOAICallback callback) {
        super(fighterHandler, command, callback);
    }

    @Override
    public void addUnit(AIUnit u) {
        super.addUnit(u);
    }

    private AIUnit getUnit() {
        for (AIUnit au : units) {
            return au;
        }
        return null;
    }

    @Override
    public void troopIdle(AIUnit u) {

        //command.mark(u.getPos(),"unit idle");
        //Collection<Enemy> enemies = command.getEnemyUnitsIn(u.getPos(), 350);
        AIFloat3 target;
        
        int i = 0;
        do {
            i++;
            if (i > 15){
                command.debug("WARNING: No target for scout.");
                u.assignTask(new FightTask(u.getPos(), command.getCurrentFrame() + 30, this));
                return;
            }
            target = new AIFloat3((float) Math.random() * clbk.getMap().getWidth() * 8, 0, (float) Math.random() * clbk.getMap().getHeight() * 8);
        } while (command.losManager.isInLos(target) || command.radarManager.isInRadar(target)
                || (command.pathfinder.findPath(u.getPos(), target, u.getUnit().getDef().getMoveData().getMaxSlope(),
                        command.pathfinder.AVOID_ENEMIES).size() <= 1));

        u.assignTask(new MoveTask(target, Integer.MAX_VALUE, this, command.pathfinder.AVOID_ENEMIES,command));

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
    public void troopIdle(AISquad s) {
        throw new RuntimeException("Wrong troopIdle called on ScoutSquad");
    }

    @Override
    public AIUnit.UnitType getType() {
        return AIUnit.UnitType.raider;
    }

}

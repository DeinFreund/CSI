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
import zkcbai.unitHandlers.units.AIUnit;
import zkcbai.unitHandlers.units.Enemy;

/**
 *
 * @author User
 */
public class RaiderSquad extends Squad {

    private static final int maxDist = 250;

    private AIFloat3 target;

    public RaiderSquad(FighterHandler fighterHandler, Command command, OOAICallback callback, AIFloat3 target) {
        super(fighterHandler, command, callback);
        this.target = target;
    }

    private AIUnit getUnit() {
        for (AIUnit au : units) {
            return au;
        }
        return null;
    }

    @Override
    public void unitIdle(AIUnit u) {
        if (!command.defenseManager.isRaiderAccessible(u.getPos())) {
            u.moveTo(command.areaManager.getArea(u.getPos()).getNearestArea(command.areaManager.RAIDER_ACCESSIBLE).getPos(),
                    command.getCurrentFrame() + 20);
            u.moveTo(target, AIUnit.OPTION_SHIFT_KEY ,command.getCurrentFrame() + 30);
            return;
        }
        AIFloat3 fpos = getUnit().getPos();
        if (u.distanceTo(fpos) > maxDist) {
            for (AIUnit au : units) {
                au.moveTo(fpos, command.getCurrentFrame() + 30);
                au.moveTo(target, AIUnit.OPTION_SHIFT_KEY ,command.getCurrentFrame() + 30);
            }
            return;
        }
        for (Enemy e : command.getEnemyUnitsIn(u.getPos(), 300)) {
            if (!e.getDef().isAbleToAttack() && command.defenseManager.isRaiderAccessible(e.getPos())) {
                for (AIUnit au : units) {
                    au.fight(e.getPos(), command.getCurrentFrame() + 60);
                    au.moveTo(target, AIUnit.OPTION_SHIFT_KEY ,command.getCurrentFrame() + 30);
                }
                return;
            }
        }
        

    }

}

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package zkcbai.unitHandlers.betterSquads;

import com.springrts.ai.oo.clb.OOAICallback;
import com.springrts.ai.oo.clb.UnitDef;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import zkcbai.Command;
import zkcbai.unitHandlers.FighterHandler;
import zkcbai.unitHandlers.units.AISquad;
import zkcbai.unitHandlers.units.AITroop;
import zkcbai.unitHandlers.units.AIUnit;
import zkcbai.unitHandlers.units.Enemy;
import zkcbai.unitHandlers.units.tasks.Task;

/**
 *
 * @author User
 */
public class SupportSquad extends SquadManager {

    public SupportSquad(FighterHandler fighterHandler, Command command, OOAICallback callback) {
        this(fighterHandler, command, callback, null);
    }

    public SupportSquad(FighterHandler fighterHandler, Command command, OOAICallback callback, Collection<UnitDef> availableUnits) {
        super(fighterHandler, command, callback, availableUnits);
        for (String s : supportIds) {
            supports.add(command.getCallback().getUnitDefByName(s));
        }
    }

    final private String[] supportIds = {"armsnipe", "amphassault", "armmanni", "firewalker", "shieldfelon", "armcrabe", "corgol", "armmerl"};
    final static Set<UnitDef> supports = new HashSet();

    private Random rnd = new Random();

    @Override
    public List<UnitDef> getRequiredUnits(Collection<UnitDef> availableUnits) {
        List<UnitDef> possible = new ArrayList();
        for (UnitDef ud : availableUnits){
            if (ud.getCost(command.metal) / (command.getCurrentFrame() / (30 * 60) + 1) > 7 * command.getBuilderHandler().getMetalIncome()) continue;
            if (supports.contains(ud)) possible.add(ud);
        }
        List<UnitDef> retval = new ArrayList();
        if (!possible.isEmpty()){
            retval.add(possible.get(rnd.nextInt(possible.size())));
        }
        return retval;
    }

    @Override
    public float getUsefulness() {
        if (command.getCurrentFrame() < 30*60*4) return 0;
        float assaultValue = 0;
        float supportValue = 0;
        for (AIUnit au : command.getUnits()){
            if (AssaultSquad.assaults.contains(au.getDef()) || AssaultSquad.riots.contains(au.getDef())){
                assaultValue += au.getMetalCost();
            }
            if (supports.contains(au.getDef())){
                supportValue += au.getMetalCost();
            }
        }
        command.debug("Support useful: " + Math.min(0.9f, -(float)Math.log(Math.max(0.00001, 1f - assaultValue / (supportValue * 2 + 1000)))));
        return Math.min(0.9f, assaultValue / (supportValue * 2 + 1000));
    }

    @Override
    public AIUnit.UnitType getType() {
        return AIUnit.UnitType.assault;
    }

    @Override
    public void troopIdle(AIUnit u) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void troopIdle(AISquad s) {
        throw new UnsupportedOperationException();
    }

    

    @Override
    public void troopIdle(AITroop t) {
        
    }

    @Override
    public void abortedTask(Task t) {
    }

    @Override
    public void finishedTask(Task t) {
    }

    @Override
    public SquadManager getInstance(Collection<UnitDef> availableUnits) {
        return new AssaultSquad(fighterHandler, command, clbk, availableUnits);
    }

    @Override
    public void unitDestroyed(Enemy e, AIUnit killer) {
        super.unitDestroyed(e, killer);
    }
    
    @Override
    public boolean retreatForRepairs(AITroop u) {
        return true;
    }
}

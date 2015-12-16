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
import java.util.Set;
import zkcbai.Command;
import zkcbai.helpers.ZoneManager.Area;
import zkcbai.unitHandlers.FighterHandler;
import zkcbai.unitHandlers.units.AISquad;
import zkcbai.unitHandlers.units.AITroop;
import zkcbai.unitHandlers.units.AIUnit;
import zkcbai.unitHandlers.units.tasks.MoveTask;
import zkcbai.unitHandlers.units.tasks.Task;

/**
 *
 * @author User
 */
public class ScoutSquad extends SquadManager {

    public ScoutSquad(FighterHandler fighterHandler, Command command, OOAICallback callback) {
        this(fighterHandler, command, callback, null);
    }
    
    
    public ScoutSquad(FighterHandler fighterHandler, Command command, OOAICallback callback, Collection<UnitDef> availableUnits) {
        super(fighterHandler, command, callback, availableUnits);
        for (String s : scoutIds){
            scouts.add(command.getCallback().getUnitDefByName(s));
        }
    }
    
    final private String[] scoutIds = {"fighter","armpw", "corak", "corfav","logkoda","blastwing","amphraider3","shipscout", "corsh" ,"armflea"};
    
    final private List<UnitDef> scouts = new ArrayList();
    
    @Override
    public List<UnitDef> getRequiredUnits(Collection<UnitDef> availableUnits) {
        int reqcnt = 2;
        for (AIUnit au : units){
            if (scouts.contains(au.getDef())){
                reqcnt --;
            }
        }
        Set<UnitDef> unitset = new HashSet(availableUnits);
        for (UnitDef ud : scouts){
            if (unitset.contains(ud)){
                List<UnitDef> req = new ArrayList();
                for (int i = 0; i < reqcnt ; i++){
                    req.add(ud);
                }
                return req;
            }
        }
        command.debug("Warning: Couldn't find any required units in set of available Units: ");
        for (UnitDef ud : availableUnits){
            command.debug(ud.getHumanName());
        }
        return null;
    }

    @Override
    public float getUsefulness() {
        int vis = 0;
        for (Area a : command.areaManager.getAreas()){
            if (a.isInLOS()){
                vis ++;
            }
        }
        return 1f - (Math.min(0.5f , Math.max( 0.1f ,(float)vis / command.areaManager.getAreas().size())) - 0.1f) / 0.4f;
    }

    @Override
    public AIUnit.UnitType getType() {
        return AIUnit.UnitType.raider;
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
    public void troopIdle(AITroop t){
        if (!getRequiredUnits(command.getCallback().getUnitDefs()).isEmpty()) {
            t.wait(command.getCurrentFrame() + 60);
            return;
        }
        for (Area a : command.areaManager.getAreas()){
            if (!a.isInLOS()){
                t.assignTask(new MoveTask(a.getPos(), command.getCurrentFrame() + 150, this, command.pathfinder.AVOID_ENEMIES, command));
                return;
            }
        }
    }

    @Override
    public void abortedTask(Task t) {
    }

    @Override
    public void finishedTask(Task t) {
    }


    @Override
    public SquadManager getInstance(Collection<UnitDef> availableUnits) {
        return new ScoutSquad(fighterHandler, command, clbk, availableUnits);
    }

    
}

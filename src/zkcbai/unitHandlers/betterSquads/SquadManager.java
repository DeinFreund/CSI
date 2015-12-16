/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package zkcbai.unitHandlers.betterSquads;

import com.springrts.ai.oo.clb.OOAICallback;
import com.springrts.ai.oo.clb.UnitDef;
import java.util.Collection;
import java.util.List;
import zkcbai.Command;
import zkcbai.unitHandlers.FighterHandler;
import zkcbai.unitHandlers.squads.SquadHandler;

/**
 *
 * @author User
 */
public abstract class SquadManager extends SquadHandler {
    //squad handler 2.0
    
    Collection<UnitDef> availableUnits;

    /**
     *
     * @param availableUnits currently buildable unitDefs
     * @return units needed for this squad, includes possible dependencies(i.e.
     * factories)<br />
     * null if it isn't possible to build the squad
     */
    public abstract List<UnitDef> getRequiredUnits(Collection<UnitDef> availableUnits);

    /**
     * Use when the construction has already begun
     * @return
     */
    public List<UnitDef> getRequiredUnits(){
        if (availableUnits == null) throw new AssertionError("Used placeholder instance as actual Squad");
        return getRequiredUnits(availableUnits);
    }
    
    /**
     *
     * @return number in [0, 1] representing effectiveness of the squad under
     * the current circumstances
     */
    public abstract float getUsefulness();

    public abstract SquadManager getInstance(Collection<UnitDef> availableUnits);
    
    /**
     * Using this constructor implies constructing a placeholder instance!
     * @param fighterHandler
     * @param command
     * @param callback
     */
    public SquadManager(FighterHandler fighterHandler, Command command, OOAICallback callback) {
        this(fighterHandler, command, callback, null);
    }
    
    public SquadManager(FighterHandler fighterHandler, Command command, OOAICallback callback, Collection<UnitDef> availableUnits) {
        super(fighterHandler, command, callback);
        this.availableUnits = availableUnits;
        if (availableUnits != null){
            fighterHandler.addSquad(this);
        }
    }
    
    @Override
    public void reportSpam() {
        throw new AssertionError("this is so outdated");
    }

}

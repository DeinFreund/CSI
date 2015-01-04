/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package zkcbai.unitHandlers.squads;

import com.springrts.ai.oo.AIFloat3;
import com.springrts.ai.oo.clb.OOAICallback;
import java.util.HashSet;
import java.util.Set;
import zkcbai.Command;
import zkcbai.unitHandlers.FighterHandler;
import zkcbai.unitHandlers.units.AIUnit;

/**
 *
 * @author User
 */
public abstract class Squad {
    //not exactly a Task but really close

    Set<AIUnit> units = new HashSet();
    OOAICallback clbk;
    Command command;
    FighterHandler fighterHandler;

    public Squad(FighterHandler fighterHandler, Command command, OOAICallback callback) {
        this.command = command;
        this.clbk = callback;
        this.fighterHandler = fighterHandler;
    }

    public AIFloat3 getPos() {
        AIFloat3 pos = new AIFloat3();
        int c = 0;
        for (AIUnit au : units) {
            pos.add(au.getPos());
            c++;
        }
        pos.scale(1f / c);
        return pos;
    }

    /**
     *
     * @param u
     * @return time to unite with aiUnit u in frames
     */
    public float timeTo(AIUnit u) {
        return u.distanceTo(getPos()) / u.getUnit().getMaxSpeed();
    }
    
    public float distanceTo(AIFloat3 trg) {
        AIFloat3 pos = new AIFloat3(getPos());
        pos.sub(trg);
        return pos.length();
    }

    public void addUnit(AIUnit u) {
        units.add(u);
        unitIdle(u);
    }

    public void removeUnit(AIUnit u) {
        units.remove(u);
    }

    public int size() {
        return units.size();
    }

    public abstract void unitIdle(AIUnit u);

}

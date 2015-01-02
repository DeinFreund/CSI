package zkcbai.unitHandlers;

import com.springrts.ai.oo.clb.OOAICallback;
import com.springrts.ai.oo.clb.Unit;
import java.util.ArrayList;
import java.util.List;
import zkcbai.Command;
import zkcbai.unitHandlers.units.AIUnit;
import zkcbai.unitHandlers.units.tasks.Task;

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

/**
 *
 * @author User
 */
public class BuilderHandler extends UnitHandler {

    List<AIUnit> builders = new ArrayList();

    public BuilderHandler(Command cmd, OOAICallback clbk) {
        super(cmd, clbk);
    }
    
    @Override
    public AIUnit addUnit(Unit u) {
        builders.add(new AIUnit(u, this));
        aiunits.put(u.getUnitId(), builders.get(builders.size()-1));
        builders.get(builders.size()-1).idle();
        return builders.get(builders.size()-1);
    }

    @Override
    public void unitIdle(AIUnit u) {
    }

    @Override
    public void abortedTask(Task t) {
    }

    @Override
    public void finishedTask(Task t) {
    }
    
}

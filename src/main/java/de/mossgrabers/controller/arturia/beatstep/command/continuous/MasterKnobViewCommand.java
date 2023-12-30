// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2017-2023
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.controller.arturia.beatstep.command.continuous;

import de.mossgrabers.controller.arturia.beatstep.BeatstepConfiguration;
import de.mossgrabers.controller.arturia.beatstep.controller.BeatstepControlSurface;
import de.mossgrabers.controller.arturia.beatstep.view.BeatstepView;
import de.mossgrabers.framework.command.core.AbstractContinuousCommand;
import de.mossgrabers.framework.daw.IModel;
import de.mossgrabers.framework.featuregroup.IView;


/**
 * Command to delegate the moves of a knob to a view.
 *
 * @author Jürgen Moßgraber
 */
public class MasterKnobViewCommand extends AbstractContinuousCommand<BeatstepControlSurface, BeatstepConfiguration>
{
    /**
     * Constructor.
     *
     * @param model The model
     * @param surface The surface
     */
    public MasterKnobViewCommand (final IModel model, final BeatstepControlSurface surface)
    {
        super (model, surface);
    }


    /** {@inheritDoc} */
    @Override
    public void execute (final int value)
    {
        final IView v = this.surface.getViewManager ().getActive ();
        if (v == null)
            return;

        double knobChange = this.model.getValueChanger ().calcKnobChange (value);
        if (knobChange == 0)
            return;

        // Try to tame the weird jumps of the knobs
        knobChange = Math.min (10, Math.max (-10, knobChange));
        ((BeatstepView) v).onMasterKnob ((int) knobChange + 64);
    }
}

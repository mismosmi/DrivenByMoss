// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2017-2024
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.controller.arturia.beatstep.view;

import java.util.ArrayList;
import java.util.Optional;

import de.mossgrabers.controller.arturia.beatstep.BeatstepConfiguration;
import de.mossgrabers.controller.arturia.beatstep.controller.BeatstepColorManager;
import de.mossgrabers.controller.arturia.beatstep.controller.BeatstepControlSurface;
import de.mossgrabers.framework.controller.grid.IPadGrid;
import de.mossgrabers.framework.daw.IModel;
import de.mossgrabers.framework.daw.clip.IStepInfo;
import de.mossgrabers.framework.daw.clip.NotePosition;
import de.mossgrabers.framework.daw.clip.StepState;
import de.mossgrabers.framework.daw.constants.Resolution;
import de.mossgrabers.framework.daw.data.ITrack;
import de.mossgrabers.framework.daw.data.bank.ITrackBank;
import de.mossgrabers.framework.scale.Scales;
import de.mossgrabers.framework.view.sequencer.AbstractSequencerView;

/**
 * The Sequencer view.
 *
 * @author Jürgen Moßgraber
 */
public class SequencerView extends AbstractSequencerView<BeatstepControlSurface, BeatstepConfiguration>
        implements BeatstepView {

    protected TrackEditing extensions;
    private static final int NUM_DISPLAY_COLS = 16;

    private int activeStep = 0;
    private boolean gridNotePressed = false;

    /**
     * Constructor.
     *
     * @param surface The controller
     * @param model   The model
     */
    public SequencerView(final BeatstepControlSurface surface, final IModel model) {
        super("Sequencer", surface, model, 128, SequencerView.NUM_DISPLAY_COLS, false);

        this.extensions = new TrackEditing(surface, model);

        final ITrackBank tb = model.getTrackBank();
        tb.addSelectionObserver((index, isSelected) -> this.keyManager.clearPressedKeys());
        tb.addNoteObserver(this::updateNote);
    }

    /** {@inheritDoc} */
    @Override
    public void onKnob (final int index, final int value)
    {
        final boolean isIncrease = this.model.getValueChanger ().isIncrease (value);
        switch (index) {
            case 8:
                int numSteps = this.getClip().getNumSteps();

                this.activeStep = Math.min(numSteps - 1,
                        Math.max(0, this.activeStep + this.model.getValueChanger().decode(value)));

                this.surface.getDisplay().notify("Step " + (this.activeStep + 1));
                break;

            case 9:
                int midiChannel = this.configuration.getMidiEditChannel();

                ArrayList<NotePosition> notes = new ArrayList<NotePosition>(5);

                for (int i = 0; i < 128; i++) {
                    final NotePosition notePosition = new NotePosition(midiChannel, this.activeStep, i);

                    IStepInfo step = this.getClip().getStep(notePosition);

                    if (step.getState() != StepState.OFF) {
                        notes.add(notePosition);
                    }
                }

                this.getClip().startEdit(notes);

                for (NotePosition note : notes) {
                    this.getClip().changeStepDuration(note, value);

                    IStepInfo step = this.getClip().getStep(note);

                    if (step.getDuration() == 0) {
                        this.getClip().clearStep(note);
                    }
                }

                this.getClip().stopEdit();
                break;

            case 10:
                if (!this.isActive())
                    return;

                final int selectedResolutionIndex = Resolution.change(this.getResolutionIndex(), isIncrease);
                this.getClip().setStepLength(Resolution.getValueAt(selectedResolutionIndex));
                this.surface.getDisplay().notify(Resolution.getNameAt(this.getResolutionIndex()));
                break;

            case 11:
                this.getClip().changeLoopLength(value, this.surface.isKnobSensitivitySlow());
                this.surface.getDisplay().notify("Length " + this.getClip().getLoopLength());
                break;

            // Chromatic
            case 12:
                this.scales.setChromatic(!isIncrease);
                this.surface.getConfiguration().setScaleInKey(isIncrease);
                this.surface.getDisplay().notify(isIncrease ? "In Key" : "Chromatic");
                break;

            // Base Note
            case 13:
                if (isIncrease)
                    this.scales.nextScaleOffset();
                else
                    this.scales.prevScaleOffset();
                final String scaleBase = Scales.BASES.get(this.scales.getScaleOffsetIndex());
                this.surface.getDisplay().notify(scaleBase);
                this.surface.getConfiguration().setScaleBase(scaleBase);
                break;

            // Scale
            case 14:
                if (isIncrease)
                    this.scales.nextScale();
                else
                    this.scales.prevScale();
                final String scale = this.scales.getScale().getName();
                this.surface.getConfiguration().setScale(scale);
                this.surface.getDisplay().notify(scale);
                break;

            // Octave
            case 15:
                this.keyManager.clearPressedKeys();
                if (isIncrease)
                    this.scales.incOctave();
                else
                    this.scales.decOctave();
                this.updateNoteMapping();
                this.surface.getDisplay().notify("Octave " + (this.scales.getOctave() > 0 ? "+" : "")
                        + this.scales.getOctave() + " (" + this.scales.getRangeText() + ")");
                break;

            // 0-11
            default:
                this.extensions.onTrackKnob (index, value);
                break;
        }
    }

    /** {@inheritDoc} */
    public void onMasterKnob(final int value) {
        this.model.getTransport().changePosition(this.model.getValueChanger().isIncrease(value), this.surface.isKnobSensitivitySlow());
    }

    /** {@inheritDoc} */
    @Override
    public void onGridNote(final int note, final int velocity) {
        if (!this.model.canSelectedTrackHoldNotes())
            return;

        if (velocity == 0) {
            if (this.gridNotePressed) {
                this.activeStep = (this.activeStep + 1) % this.getClip().getNumSteps();
            }
            this.gridNotePressed = false;
            return;
        }

        if (!this.gridNotePressed) {
            this.clearActiveStep();
        }

        this.gridNotePressed = true;

        int mappedNote = this.keyManager.map(note);
        // Mark selected notes
        for (int i = 0; i < 128; i++) {
            if (mappedNote == this.keyManager.map(i))
                this.keyManager.setKeyPressed(i, velocity);
        }

        if (velocity != 0) {
            final int map = this.scales.getNoteMatrix()[note];
            final NotePosition notePosition = new NotePosition(this.configuration.getMidiEditChannel(), this.activeStep,
                    map);
            this.getClip().toggleStep(notePosition,
                    this.configuration.isAccentActive() ? this.configuration.getFixedAccentValue() : velocity);
        }
    }

    /** {@inheritDoc} */
    @Override
    public void updateNoteMapping() {
        this.delayedUpdateNoteMapping(
                this.model.canSelectedTrackHoldNotes() ? this.scales.getNoteMatrix()
                        : Scales.getEmptyMatrix());
    }

    /** {@inheritDoc} */
    @Override
    public void drawGrid() {
        final IPadGrid padGrid = this.surface.getPadGrid();
        if (!this.model.canSelectedTrackHoldNotes()) {
            padGrid.turnOff();
            return;
        }

        int[] noteMatrix = this.scales.getNoteMatrix();

        for (int i = 36; i < 52; i++) {
            final NotePosition notePosition = new NotePosition(this.configuration.getMidiEditChannel(), this.activeStep,
                    noteMatrix[i]);
            IStepInfo step = this.getClip().getStep(notePosition);
            padGrid.light(i,
                    this.keyManager.isKeyPressed(i) || step.getState() != StepState.OFF
                            ? BeatstepColorManager.BEATSTEP_BUTTON_STATE_PINK
                            : this.colorManager.getColorIndex(this.keyManager.getColor(i)));
        }
    }

    /**
     * The callback function for playing note changes.
     *
     * @param trackIndex The index of the track on which the note is playing
     * @param note       The played note
     * @param velocity   The played velocity
     */
    private void updateNote(final int trackIndex, final int note, final int velocity) {
        final Optional<ITrack> sel = this.model.getCurrentTrackBank().getSelectedItem();
        if (sel.isEmpty() || sel.get().getIndex() != trackIndex)
            return;

        // Light notes sent from the sequencer
        for (int i = 0; i < 128; i++) {
            if (this.keyManager.map(i) == note)
                this.keyManager.setKeyPressed(i, velocity);
        }
    }

    private void clearActiveStep() {
        final int channel = this.configuration.getMidiEditChannel();
        ArrayList<NotePosition> notes = new ArrayList<NotePosition>(5);

        for (int i = 0; i < 128; i++) {
            notes.add(new NotePosition(channel, this.activeStep, i));
        }

        this.getClip().startEdit(notes);
            
        for (NotePosition currentNote : notes) {
            this.getClip().clearStep(currentNote);
        }

        this.getClip().stopEdit();
    }
}
package com.spitfyre.gander.interactions.buttons;

import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;

import java.util.List;

public interface IButtonInteraction {
    List<Button> getButtons();

    void onInteract(ButtonInteractionEvent event);
}

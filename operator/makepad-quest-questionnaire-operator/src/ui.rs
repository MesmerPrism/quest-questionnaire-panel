use makepad_widgets::*;

script_mod! {
    use mod.prelude.widgets.*
    use mod.widgets.*

    mod.widgets.OperatorFieldLabel = Label{
        draw_text.color: #x4b5563
        draw_text.text_style.font_size: 8.0
    }

    mod.widgets.OperatorCenteredField = TextInput{
        width: Fill
        height: 32
        margin: Inset{top: 1.0 bottom: 4.0}
        padding: Inset{right: 9.0 left: 9.0}
        label_align: Align{x: 0.5 y: 0.5}
        text_placement: TextInputTextPlacement.InnerAlign
        draw_text +: {
            color: #x111827
            text_style.font_size: 9.0
        }
        draw_bg +: {
            border_radius: 5.0
            color: #xffffff
            color_hover: #xffffff
            color_focus: #xffffff
            color_empty: #xffffff
            border_color: #xcbd5e1
            border_color_hover: #x94a3b8
            border_color_focus: #x0f766e
            border_color_empty: #xcbd5e1
        }
    }

    mod.widgets.OperatorPrimaryButton = Button{
        height: 30
        padding: Inset{left: 10.0 right: 10.0}
        draw_text +: {
            color: #xffffff
            color_hover: #xffffff
            color_down: #xffffff
            text_style.font_size: 9.0
        }
        draw_bg +: {
            border_radius: 5.0
            color: #x0f766e
            color_hover: #x115e59
            color_down: #x134e4a
            border_color: #x0f766e
            border_color_hover: #x115e59
            border_color_down: #x134e4a
        }
    }

    mod.widgets.OperatorSecondaryButton = Button{
        height: 30
        padding: Inset{left: 10.0 right: 10.0}
        draw_text +: {
            color: #x111827
            color_hover: #x111827
            color_down: #x111827
            text_style.font_size: 9.0
        }
        draw_bg +: {
            border_radius: 5.0
            color: #xf3f4f6
            color_hover: #xe5e7eb
            color_down: #xd1d5db
            border_color: #xd1d5db
            border_color_hover: #x9ca3af
            border_color_down: #x9ca3af
        }
    }

    mod.widgets.OperatorPanel = RoundedView{
        width: Fill
        height: Fit
        flow: Down
        spacing: 4.0
        padding: Inset{top: 8.0 right: 8.0 bottom: 8.0 left: 8.0}
        margin: Inset{bottom: 8.0}
        draw_bg.color: #xffffff
        draw_bg.border_color: #xd7dde5
        draw_bg.border_size: 1.0
        draw_bg.border_radius: 6.0
    }

    mod.widgets.OperatorSectionTitle = Label{
        draw_text.color: #x111827
        draw_text.text_style.font_size: 11.0
    }

    mod.widgets.OperatorStatusValue = Label{
        width: Fill
        draw_text.color: #x374151
        draw_text.text_style.font_size: 9.0
    }

    mod.widgets.OperatorMonoValue = Label{
        width: Fill
        draw_text.color: #x374151
        draw_text.text_style.font_size: 8.0
    }
}

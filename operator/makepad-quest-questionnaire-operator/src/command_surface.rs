#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub struct CommandSurfaceItem {
    pub gui_control: &'static str,
    pub cli_equivalent: &'static str,
}

pub const GUI_COMMAND_SURFACE: &[CommandSurfaceItem] = &[
    CommandSurfaceItem {
        gui_control: "Endpoint field",
        cli_equivalent: "--endpoint",
    },
    CommandSurfaceItem {
        gui_control: "Session field",
        cli_equivalent: "--session-id",
    },
    CommandSurfaceItem {
        gui_control: "Participant field",
        cli_equivalent: "--participant-ref",
    },
    CommandSurfaceItem {
        gui_control: "Language field",
        cli_equivalent: "--language-code",
    },
    CommandSurfaceItem {
        gui_control: "Poll button",
        cli_equivalent: "status",
    },
    CommandSurfaceItem {
        gui_control: "Dismiss button",
        cli_equivalent: "dismiss",
    },
    CommandSurfaceItem {
        gui_control: "ADB serial field",
        cli_equivalent: "--serial",
    },
    CommandSurfaceItem {
        gui_control: "Panel APK field",
        cli_equivalent: "--apk",
    },
    CommandSurfaceItem {
        gui_control: "Install Panel button",
        cli_equivalent: "install-panel",
    },
    CommandSurfaceItem {
        gui_control: "Host port field",
        cli_equivalent: "--host-port",
    },
    CommandSurfaceItem {
        gui_control: "Quest port field",
        cli_equivalent: "--device-port",
    },
    CommandSurfaceItem {
        gui_control: "Tools button",
        cli_equivalent: "tooling-status",
    },
    CommandSurfaceItem {
        gui_control: "Devices button",
        cli_equivalent: "devices",
    },
    CommandSurfaceItem {
        gui_control: "Status button",
        cli_equivalent: "device-status",
    },
    CommandSurfaceItem {
        gui_control: "Forward button",
        cli_equivalent: "bridge-forward",
    },
    CommandSurfaceItem {
        gui_control: "Open Block 1 button",
        cli_equivalent: "open-block --block 1",
    },
    CommandSurfaceItem {
        gui_control: "Open Block 2 button",
        cli_equivalent: "open-block --block 2",
    },
    CommandSurfaceItem {
        gui_control: "Open Block 3 button",
        cli_equivalent: "open-block --block 3",
    },
];

#[cfg(test)]
mod tests {
    use super::*;
    use crate::cli;

    #[test]
    fn gui_surface_has_cli_equivalents_in_help_text() {
        let help = cli::run(vec!["help".to_string()]).unwrap();
        for item in GUI_COMMAND_SURFACE {
            let command = item
                .cli_equivalent
                .split_whitespace()
                .next()
                .unwrap_or(item.cli_equivalent);
            assert!(
                help.contains(command),
                "{} should be covered by CLI help entry `{}`",
                item.gui_control,
                item.cli_equivalent
            );
        }
    }
}

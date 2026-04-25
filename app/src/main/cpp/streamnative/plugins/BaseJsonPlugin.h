#pragma once

#include "StreamPlugin.h"

namespace streamnative {

class BaseJsonPlugin : public StreamPlugin {
public:
    PluginState state() const override { return PluginState::IDLE; }
    bool processChar(char16_t, bool) override { return true; }
    bool initPlugin() override { return true; }
    void reset() override {}
};

} // namespace streamnative

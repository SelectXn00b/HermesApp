#pragma once

namespace streamnative {

enum class PluginState {
    IDLE,
    TRYING,
    PROCESSING,
    WAITFOR,
};

class StreamPlugin {
public:
    virtual ~StreamPlugin() = default;
    virtual PluginState state() const = 0;
    virtual bool processChar(char16_t c, bool atStartOfLine) = 0;
    virtual bool initPlugin() = 0;
    virtual void reset() = 0;
};

} // namespace streamnative

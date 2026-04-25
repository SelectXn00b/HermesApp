#pragma once

#include <string>
#include <vector>

namespace streamnative {

class KmpMatcher {
public:
    void reset() { j_ = 0; }

    void setPattern(std::u16string p) {
        pattern_ = std::move(p);
        pi_.assign(pattern_.size(), 0);
        for (size_t i = 1; i < pattern_.size(); i++) {
            int k = pi_[i - 1];
            while (k > 0 && pattern_[i] != pattern_[static_cast<size_t>(k)]) {
                k = pi_[static_cast<size_t>(k - 1)];
            }
            if (pattern_[i] == pattern_[static_cast<size_t>(k)]) {
                k++;
            }
            pi_[i] = k;
        }
        j_ = 0;
    }

    bool process(char16_t c) {
        if (pattern_.empty()) {
            return false;
        }
        while (j_ > 0 && c != pattern_[static_cast<size_t>(j_)]) {
            j_ = pi_[static_cast<size_t>(j_ - 1)];
        }
        if (c == pattern_[static_cast<size_t>(j_)]) {
            j_++;
        }
        if (j_ == static_cast<int>(pattern_.size())) {
            j_ = pi_[static_cast<size_t>(j_ - 1)];
            return true;
        }
        return false;
    }

private:
    std::u16string pattern_;
    std::vector<int> pi_;
    int j_ = 0;
};

} // namespace streamnative

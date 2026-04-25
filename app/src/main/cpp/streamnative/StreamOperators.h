#pragma once

#include <jni.h>
#include <vector>

#include "StreamGroup.h"

namespace streamnative {

std::vector<Segment> splitByXml(const jchar* chars, int len);

class MarkdownSession;

MarkdownSession* createMarkdownBlockSession();
MarkdownSession* createMarkdownInlineSession();
void destroyMarkdownSession(MarkdownSession* session);

std::vector<Segment> markdownSessionPush(MarkdownSession* session, const jchar* chars, int len);

} // namespace streamnative

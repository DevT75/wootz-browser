// Copyright 2016 The Chromium Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef CONTENT_WEB_TEST_RENDERER_PIXEL_DUMP_H_
#define CONTENT_WEB_TEST_RENDERER_PIXEL_DUMP_H_

#include "printing/page_range.h"

class SkBitmap;

namespace blink {
class WebLocalFrame;
struct WebPrintParams;
}  // namespace blink

namespace content {

// Goes through a test-only path to dump the frame's pixel output as if it was
// printed.
SkBitmap PrintFrameToBitmap(blink::WebLocalFrame* web_frame,
                            const blink::WebPrintParams& print_params,
                            const printing::PageRanges& pages);

}  // namespace content

#endif  // CONTENT_WEB_TEST_RENDERER_PIXEL_DUMP_H_

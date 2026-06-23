#include <gtest/gtest.h>
#include <string>
#include <vector>
#include <cstdint>
#include <climits>

extern "C" {
    // Declare the actual function from ffmpeg_jni.cc
    // Assuming the vulnerable function is named 'decode_buffer'
    void* decode_buffer(size_t width, size_t height, size_t stride);
}

class SecurityTest : public ::testing::TestWithParam<std::tuple<size_t, size_t, size_t>> {};

TEST_P(SecurityTest, AllocationSizeOverflowCheck) {
    // Invariant: Allocation size computation must not overflow
    auto [width, height, stride] = GetParam();
    
    // The property: if width * height * stride would overflow size_t,
    // the function must detect it and fail safely (return nullptr or throw)
    void* result = decode_buffer(width, height, stride);
    
    // Check for safe failure on overflow cases
    bool would_overflow = false;
    if (width > 0 && height > 0 && stride > 0) {
        if (width > SIZE_MAX / height) {
            would_overflow = true;
        } else {
            size_t product = width * height;
            if (product > SIZE_MAX / stride) {
                would_overflow = true;
            }
        }
    }
    
    if (would_overflow) {
        // On overflow, result must be nullptr (safe failure)
        EXPECT_EQ(result, nullptr) 
            << "Function should return nullptr on allocation size overflow";
    } else {
        // Valid inputs may return buffer or nullptr based on other constraints
        // No assertion here - just ensure no crash occurred
        SUCCEED();
    }
}

INSTANTIATE_TEST_SUITE_P(
    AdversarialInputs,
    SecurityTest,
    ::testing::Values(
        // Exact exploit case: values causing multiplication overflow
        std::make_tuple(65536, 65536, 4),  // 65536*65536*4 overflows 32-bit
        
        // Boundary case: just below overflow
        std::make_tuple(65535, 65535, 4),  // Should not overflow
        
        // Another overflow case: large width with moderate height/stride
        std::make_tuple(SIZE_MAX, 2, 1),   // Definitely overflows
        
        // Valid normal input
        std::make_tuple(1920, 1080, 4),    // Normal HD frame
        
        // Edge case: zero dimensions (should be handled)
        std::make_tuple(0, 1080, 4)
    )
);

int main(int argc, char **argv) {
    ::testing::InitGoogleTest(&argc, argv);
    return RUN_ALL_TESTS();
}
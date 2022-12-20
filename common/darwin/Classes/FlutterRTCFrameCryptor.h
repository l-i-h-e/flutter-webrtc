#if TARGET_OS_IPHONE
#import <Flutter/Flutter.h>
#elif TARGET_OS_OSX
#import <FlutterMacOS/FlutterMacOS.h>
#endif

#import <Foundation/Foundation.h>
#import <WebRTC/WebRTC.h>

#import "FlutterWebRTCPlugin.h"

@interface FlutterWebRTCPlugin (FrameCryptor)

- (void)handleFrameCryptorMethodCall:(nonnull FlutterMethodCall*)call result:(nonnull FlutterResult)result;

- (void)frameCryptorFactoryCreateFrameCryptor:(nonnull NSDictionary*)constraints
                                       result:(nonnull FlutterResult)result;

- (void)frameCryptorSetKeyIndex:(nonnull NSDictionary*)constraints
                        result:(nonnull FlutterResult)result;

- (void)frameCryptorGetKeyIndex:(nonnull NSDictionary*)constraints
                        result:(nonnull FlutterResult)result;

- (void)frameCryptorSetEnabled:(nonnull NSDictionary*)constraints
                          result:(nonnull FlutterResult)result; 

- (void)frameCryptorGetEnabled:(nonnull NSDictionary*)constraints
                            result:(nonnull FlutterResult)result;   

- (void)frameCryptorDispose:(nonnull NSDictionary*)constraints
                            result:(nonnull FlutterResult)result;

- (void)frameCryptorFactoryCreateKeyManager:(nonnull NSDictionary*)constraints
                            result:(nonnull FlutterResult)result;

- (void)keyManagerSetKey:(nonnull NSDictionary*)constraints
                            result:(nonnull FlutterResult)result;

- (void)keyManagerSetKeys:(nonnull NSDictionary*)constraints
                            result:(nonnull FlutterResult)result;
                        
- (void)keyManagerGetKeys:(nonnull NSDictionary*)constraints
                            result:(nonnull FlutterResult)result;   

- (void)keyManagerDispose:(nonnull NSDictionary*)constraints
                            result:(nonnull FlutterResult)result;

@end
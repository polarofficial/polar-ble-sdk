Pod::Spec.new do |s|  
    s.name              = 'PolarBleSdk'
    s.version           = '3.2.0'
    s.summary           = 'SDK for Polar sensors'
    s.homepage          = 'https://github.com/polarofficial/polar-ble-sdk'
    s.license           = { :type => 'Custom', :file => 'Polar_SDK_License.txt' }
    s.authors           = 'Polar Electro Oy'  
    s.swift_versions    = '5.0'
    s.cocoapods_version = '>= 1.9'
    s.source            = { :git => 'https://github.com/polarofficial/polar-ble-sdk.git', :tag => s.version.to_s }
    s.ios.deployment_target = '12.0'
    s.ios.vendored_frameworks = 'polar-sdk-ios/PolarBleSdk.xcframework'
    s.dependency 'RxSwift', '6.2.0'
end 

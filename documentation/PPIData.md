[Back to main page](../README.md)

# PPI Data in Polar devices

Pulse-to-Pulse interval is the signal between two main peaks of the Photoplethysmography (PPG) signal obtained by optical heart rate sensor used in Polar wrist devices or optical HR sensors such as Verity Sense and OH1.

It is very similar to the R-R interval between two R peaks of QRS complex calcualted by electrocardiography (ECG) based sensors (H9/H10) only not in the same phase.

The other main difference is that R-R intervals that is much easier to detect with ECG based sensors, as the R wave peaks have usually a great amplitude compared to the rest of the signal as long as the strap is moisturized and has good contact with the skin. 

On the other hand, 

> [!IMPORTANT]
> 
> P-P intervals are much more sensitive to noise (movement, light leaking if not tight enough, etc.) compared to R-R intervals. It means that this metric **cannot** be measured accurately during activity. It should only be used at complete rest.

Each PPI sample that can be fetched using Polar BLE SDK has 2 important flags : 

- The skin contact supported flag : indicates if the skin contact flag can be used to know if skin contact is present on the sample.
- The skin contact flag : indicates if there was skin contact present for that sample (only if flag above is 1).
- The blocker flag : the blocker flag is set to 1 if there was movement detected during the acquisition.

> [!WARNING]
>
> Some older generation optical sensors such as Verity Sense and OH1 might expose that skin contact is supported, but that cannot be trusted. Skin contact is quite unreliable with these devices.

If skin contact flag is 0 or blocker flag is 1, the sample should not be treated as valid and discarded.

> [!TIP]
>
> It should be advised to end user of the SDK based application to not move during any PPI data acquisition. If the SDK application sees several samples with 
> blocker = 1 in a row, it could use that to inform the user that they should try to be more still. 